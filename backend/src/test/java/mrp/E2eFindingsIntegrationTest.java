package mrp;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = {"app.bootstrap-admin.username=", "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class E2eFindingsIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("mrp_e2e")
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("../database/mrp.sql").toAbsolutePath()),
                    "/docker-entrypoint-initdb.d/01_mrp.sql");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    long staffUser, staff, managerUser, manager, material, product, request;

    long id(String sql, Object... args) { return jdbc.queryForObject(sql + " RETURNING id", Long.class, args); }

    @BeforeEach
    void fixture() {
        String name = UUID.randomUUID().toString();
        staffUser = id("INSERT INTO users(username,password_hash,role) VALUES (?,'test-only','STAFF')", "s"+name);
        managerUser = id("INSERT INTO users(username,password_hash,role) VALUES (?,'test-only','WAREHOUSE_MANAGER')", "w"+name);
        staff = id("INSERT INTO staff(user_id,full_name) VALUES (?,'Test staff')", staffUser);
        manager = id("INSERT INTO warehouse_managers(user_id,full_name) VALUES (?,'Test manager')", managerUser);
        long category = id("INSERT INTO categories(name) VALUES (?)", name);
        product = id("INSERT INTO products(category_id,name,unit,selling_price) VALUES (?,'Test','piece',1)", category);
        material = id("INSERT INTO materials(code,name,unit) VALUES (?,'Test','kg')", name);
        long bom = id("INSERT INTO boms(product_id,version,is_active) VALUES (?,1,true)", product);
        jdbc.update("INSERT INTO bom_items(bom_id,material_id,quantity) VALUES (?,?,1)", bom, material);
        request = id("INSERT INTO production_requests(product_id,bom_id,quantity,warehouse_manager_id,assigned_to) VALUES (?,?,1,?,?)",
                product, bom, manager, staff);
    }

    MockHttpServletRequestBuilder asStaff(MockHttpServletRequestBuilder r) {
        return r.with(jwt().jwt(j -> j.claim("uid", staffUser).claim("pid", staff).claim("role", "STAFF"))
                .authorities(new SimpleGrantedAuthority("ROLE_STAFF")));
    }

    void race(String path, String body) throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            var task = (java.util.concurrent.Callable<Integer>) () -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return mvc.perform(asStaff(post(path)).contentType("application/json").content(body))
                        .andReturn().getResponse().getStatus();
            };
            var first = pool.submit(task);
            var second = pool.submit(task);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
    }

    @Test void concurrentMaterialIssuesRespectBom() throws Exception {
        race("/api/material-issue-requests", "{\"productionRequestId\":"+request+",\"items\":[{\"materialId\":"+material+",\"quantity\":1}]}");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM material_issue_requests WHERE production_request_id=?", Integer.class, request)).isEqualTo(1);
    }

    @Test void concurrentProductionsRespectRequestQuantity() throws Exception {
        race("/api/productions", "{\"productionRequestId\":"+request+",\"quantity\":1}");
        assertThat(jdbc.queryForObject("SELECT sum(quantity) FROM productions WHERE production_request_id=?", Integer.class, request)).isEqualTo(1);
    }

    @Test void fractionalFinishedGoodsAndProductionQuantitiesAreRejected() throws Exception {
        assertThat(mvc.perform(asStaff(post("/api/fg-issue-requests")).contentType("application/json")
                .content("{\"items\":[{\"productId\":"+product+",\"quantity\":1.5}]}"))
                .andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(mvc.perform(asStaff(post("/api/productions")).contentType("application/json")
                .content("{\"productionRequestId\":"+request+",\"quantity\":1.5}"))
                .andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(mvc.perform(post("/api/production-requests")
                .with(jwt().jwt(j -> j.claim("uid", managerUser).claim("pid", manager).claim("role", "WAREHOUSE_MANAGER"))
                        .authorities(new SimpleGrantedAuthority("ROLE_WAREHOUSE_MANAGER")))
                .contentType("application/json").content("{\"productId\":"+product+",\"assignedStaffId\":"+staff+",\"quantity\":1.5}"))
                .andReturn().getResponse().getStatus()).isEqualTo(400);
    }

    @Test void materialQuantitiesKeepThreeDecimalPlaces() throws Exception {
        assertThat(mvc.perform(asStaff(post("/api/material-issue-requests")).contentType("application/json")
                .content("{\"productionRequestId\":"+request+",\"items\":[{\"materialId\":"+material+",\"quantity\":0.125}]}"))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT quantity FROM material_issue_request_items WHERE material_id=?",
                java.math.BigDecimal.class, material)).isEqualByComparingTo("0.125");
    }

    @Test void missingWriteRoutesReturn404AndUnsupportedMethodsReturn405() throws Exception {
        for (String path : List.of("/api/stock-movements/1", "/api/inventories/materials/1")) {
            assertThat(mvc.perform(asStaff(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path))
                    .contentType("application/json").content("{}"))
                    .andReturn().getResponse().getStatus()).isEqualTo(404);
        }
        assertThat(mvc.perform(asStaff(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/stock-movements"))
                .contentType("application/json").content("{}"))
                .andReturn().getResponse().getStatus()).isEqualTo(405);
    }
}
