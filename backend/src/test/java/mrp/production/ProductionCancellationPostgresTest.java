package mrp.production;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Run only against a disposable PostgreSQL database initialized with database/mrp.sql. */
@SpringBootTest(properties = {"app.bootstrap-admin.username=", "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "MRP_REGRESSION_DB_URL", matches = "jdbc:postgresql:.*")
class ProductionCancellationPostgresTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("MRP_REGRESSION_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("MRP_REGRESSION_DB_USER"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("MRP_REGRESSION_DB_PASSWORD", ""));
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    long managerUser, manager, staffUser, staff, material, request;

    long id(String sql, Object... args) { return jdbc.queryForObject(sql + " RETURNING id", Long.class, args); }

    @BeforeEach
    void fixture() {
        String code = UUID.randomUUID().toString();
        managerUser = id("INSERT INTO users(username,password_hash,role) VALUES (?,'regression','WAREHOUSE_MANAGER')", "w" + code);
        staffUser = id("INSERT INTO users(username,password_hash,role) VALUES (?,'regression','STAFF')", "s" + code);
        manager = id("INSERT INTO warehouse_managers(user_id,full_name) VALUES (?,'Regression manager')", managerUser);
        staff = id("INSERT INTO staff(user_id,full_name) VALUES (?,'Regression staff')", staffUser);
        long category = id("INSERT INTO categories(name) VALUES (?)", code);
        long product = id("INSERT INTO products(category_id,name,unit,selling_price) VALUES (?,'Regression product','piece',1)", category);
        material = id("INSERT INTO materials(code,name,unit) VALUES (?,'Regression material','kg')", code);
        long bom = id("INSERT INTO boms(product_id,version) VALUES (?,1)", product);
        jdbc.update("INSERT INTO bom_items(bom_id,material_id,quantity) VALUES (?,?,1)", bom, material);
        request = id("INSERT INTO production_requests(product_id,bom_id,quantity,warehouse_manager_id,assigned_to) VALUES (?,?,1,?,?)",
                product, bom, manager, staff);
        jdbc.update("INSERT INTO stock_movements(material_id,movement_type,quantity,created_by,note) VALUES (?,'ADJUST_IN',1,?,'Regression opening stock')",
                material, managerUser);
    }

    MockHttpServletRequestBuilder asManager(MockHttpServletRequestBuilder builder) {
        return builder.with(jwt().jwt(j -> j.claim("uid", managerUser).claim("pid", manager).claim("role", "WAREHOUSE_MANAGER"))
                .authorities(new SimpleGrantedAuthority("ROLE_WAREHOUSE_MANAGER")));
    }

    long pendingIssue() throws Exception {
        var response = mvc.perform(post("/api/material-issue-requests")
                .with(jwt().jwt(j -> j.claim("uid", staffUser).claim("pid", staff).claim("role", "STAFF"))
                        .authorities(new SimpleGrantedAuthority("ROLE_STAFF")))
                .contentType("application/json")
                .content("{\"productionRequestId\":" + request + ",\"items\":[{\"materialId\":" + material + ",\"quantity\":1}]}"))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        return json.readTree(response.getContentAsString()).get("id").asLong();
    }

    int cancel() throws Exception {
        return mvc.perform(asManager(post("/api/production-requests/" + request + "/cancel")))
                .andReturn().getResponse().getStatus();
    }

    int approve(long issue) throws Exception {
        return mvc.perform(asManager(post("/api/material-issue-requests/" + issue + "/approve")))
                .andReturn().getResponse().getStatus();
    }

    void assertStock(int expected, int movements) {
        assertThat(jdbc.queryForObject("SELECT quantity FROM inventories WHERE material_id=?", java.math.BigDecimal.class, material))
                .isEqualByComparingTo(String.valueOf(expected));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_movements WHERE material_id=? AND movement_type='OUT'", Integer.class, material))
                .isEqualTo(movements);
    }

    @Test
    void cancelCascadesToPendingIssuesAndApprovalCannotSubtractStock() throws Exception {
        long issue = pendingIssue();
        assertThat(cancel()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT status FROM material_issue_requests WHERE id=?", String.class, issue)).isEqualTo("CANCELLED");
        assertThat(approve(issue)).isEqualTo(409);
        assertStock(1, 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCELLED", "COMPLETED"})
    void legacyPendingIssueForClosedRequestCannotBeApproved(String status) throws Exception {
        long issue = pendingIssue();
        jdbc.update("UPDATE production_requests SET status=? WHERE id=?", status, request);
        assertThat(approve(issue)).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT status FROM material_issue_requests WHERE id=?", String.class, issue)).isEqualTo("PENDING");
        assertStock(1, 0);
    }

    @Test
    void approvalBeforeCancellationPreservesApprovedIssueAndStockMovement() throws Exception {
        long issue = pendingIssue();
        assertThat(approve(issue)).isEqualTo(200);
        assertThat(cancel()).isEqualTo(409);
        assertStock(0, 1);
    }

    @RepeatedTest(5)
    void simultaneousCancellationAndApprovalHaveOneConsistentOutcome() throws Exception {
        long issue = pendingIssue();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            Callable<Integer> cancelTask = () -> { ready.countDown(); assertThat(start.await(10, TimeUnit.SECONDS)).isTrue(); return cancel(); };
            Callable<Integer> approveTask = () -> { ready.countDown(); assertThat(start.await(10, TimeUnit.SECONDS)).isTrue(); return approve(issue); };
            var cancellation = pool.submit(cancelTask);
            var approval = pool.submit(approveTask);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(cancellation.get(20, TimeUnit.SECONDS), approval.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        String requestStatus = jdbc.queryForObject("SELECT status FROM production_requests WHERE id=?", String.class, request);
        String issueStatus = jdbc.queryForObject("SELECT status FROM material_issue_requests WHERE id=?", String.class, issue);
        if (requestStatus.equals("CANCELLED")) {
            assertThat(issueStatus).isEqualTo("CANCELLED");
            assertStock(1, 0);
        } else {
            assertThat(requestStatus).isEqualTo("PENDING");
            assertThat(issueStatus).isEqualTo("APPROVED");
            assertStock(0, 1);
        }
    }
}
