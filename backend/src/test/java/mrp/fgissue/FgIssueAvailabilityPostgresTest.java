package mrp.fgissue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.bootstrap-admin.username=", "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class FgIssueAvailabilityPostgresTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("mrp_fg_availability")
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("../database/mrp.sql").toAbsolutePath()),
                    "/docker-entrypoint-initdb.d/01_mrp.sql");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    long staffUser, staff, managerUser, manager, product;

    long id(String sql, Object... args) {
        return jdbc.queryForObject(sql + " RETURNING id", Long.class, args);
    }

    @BeforeEach
    void stockAndUsers() {
        String suffix = UUID.randomUUID().toString();
        staffUser = id("INSERT INTO users(username,password_hash,role) VALUES (?,'test-only','STAFF')", "s" + suffix);
        managerUser = id("INSERT INTO users(username,password_hash,role) VALUES (?,'test-only','WAREHOUSE_MANAGER')", "w" + suffix);
        staff = id("INSERT INTO staff(user_id,full_name) VALUES (?,'FG regression staff')", staffUser);
        manager = id("INSERT INTO warehouse_managers(user_id,full_name) VALUES (?,'FG regression manager')", managerUser);
        long category = id("INSERT INTO categories(name) VALUES (?)", suffix);
        product = id("INSERT INTO products(category_id,name,unit,selling_price) VALUES (?,'FG regression product','piece',1)", category);
        long material = id("INSERT INTO materials(code,name,unit) VALUES (?,'FG regression material','kg')", suffix);
        long bom = id("INSERT INTO boms(product_id,version,is_active) VALUES (?,1,true)", product);
        jdbc.update("INSERT INTO bom_items(bom_id,material_id,quantity) VALUES (?,?,1)", bom, material);
        addStock(product, 10);
    }

    void addStock(long productId, int quantity) {
        jdbc.update("INSERT INTO stock_movements(product_id,movement_type,quantity,created_by,note) "
                + "VALUES (?,'ADJUST_IN',?,?,'FG regression stock')", productId, quantity, managerUser);
    }

    MockHttpServletRequestBuilder asStaff(MockHttpServletRequestBuilder builder) {
        return builder.with(jwt().jwt(j -> j.subject("staff").claim("uid", staffUser).claim("pid", staff).claim("role", "STAFF"))
                .authorities(new SimpleGrantedAuthority("ROLE_STAFF")));
    }

    MockHttpServletRequestBuilder asManager(MockHttpServletRequestBuilder builder) {
        return builder.with(jwt().jwt(j -> j.subject("manager").claim("uid", managerUser).claim("pid", manager).claim("role", "WAREHOUSE_MANAGER"))
                .authorities(new SimpleGrantedAuthority("ROLE_WAREHOUSE_MANAGER")));
    }

    JsonNode create(int quantity) throws Exception {
        return createItems("{\"productId\":" + product + ",\"quantity\":" + quantity + "}");
    }

    JsonNode createItems(String items) throws Exception {
        var response = mvc.perform(asStaff(post("/api/fg-issue-requests"))
                .contentType("application/json").content("{\"note\":\"Regression customer order\",\"items\":[" + items + "]}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        return json.readTree(response.getContentAsString());
    }

    JsonNode detail(long requestId, boolean asManager) throws Exception {
        var request = get("/api/fg-issue-requests/" + requestId);
        var response = mvc.perform(asManager ? asManager(request) : asStaff(request))
                .andExpect(status().isOk()).andReturn().getResponse();
        return json.readTree(response.getContentAsString());
    }

    void assertWarning(JsonNode response, boolean expected) {
        assertThat(response.get("hasStockShortage").asBoolean()).isEqualTo(expected);
        assertThat(response.get("warning").isNull()).isEqualTo(!expected);
    }

    void assertStock(int expected, int outMovements) {
        assertThat(jdbc.queryForObject("SELECT quantity FROM inventories WHERE product_id=?", BigDecimal.class, product))
                .isEqualByComparingTo(String.valueOf(expected));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_movements WHERE product_id=? AND movement_type='OUT'", Integer.class, product))
                .isEqualTo(outMovements);
    }

    @Test
    void warningSurvivesReopeningAndMatchesMrpReservations() throws Exception {
        assertWarning(create(8), false);
        var created = create(5);
        assertWarning(created, true);
        long request = created.get("id").asLong();
        for (boolean managerView : new boolean[]{false, true}) {
            var reopened = detail(request, managerView);
            assertWarning(reopened, true);
            assertThat(reopened.get("warning")).isEqualTo(created.get("warning"));
            assertThat(reopened.get("items").get(0).get("currentStock").decimalValue()).isEqualByComparingTo("10");
        }
        var plan = mvc.perform(asManager(get("/api/mrp/plan").param("productId", String.valueOf(product)).param("quantity", "10")))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(json.readTree(plan.getContentAsString()).get("fgReserved").decimalValue()).isEqualByComparingTo("13");
        assertStock(10, 0);
    }

    @ParameterizedTest
    @CsvSource({"0,6", "5,5"})
    void requestIsCountedOnceAndExactBalanceDoesNotWarn(int other, int own) throws Exception {
        if (other > 0) create(other);
        var created = create(own);
        assertWarning(created, false);
        assertWarning(detail(created.get("id").asLong(), false), false);
        assertStock(10, 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"cancel", "reject"})
    void closingOtherPendingRequestReleasesAvailability(String action) throws Exception {
        long other = create(8).get("id").asLong();
        var created = create(5);
        assertWarning(created, true);
        var request = post("/api/fg-issue-requests/" + other + "/" + action);
        var response = mvc.perform(action.equals("cancel") ? asStaff(request)
                : asManager(request).contentType("application/json").content("{\"reason\":\"Regression rejection\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertWarning(json.readTree(response.getContentAsString()), false);
        assertWarning(detail(other, true), false);
        assertWarning(detail(created.get("id").asLong(), true), false);
        assertStock(10, 0);
    }

    @Test
    void replenishmentClearsWarningOnlyWhenTotalPendingDemandIsCovered() throws Exception {
        create(8);
        long request = create(5).get("id").asLong();
        addStock(product, 2);
        assertWarning(detail(request, true), true);
        addStock(product, 1);
        assertWarning(detail(request, true), false);
        assertStock(13, 0);
    }

    @Test
    void approvalUsesPhysicalStockAndClosedRequestDoesNotWarnOrReserveAgain() throws Exception {
        create(8);
        long request = create(6).get("id").asLong();
        assertWarning(detail(request, true), true);
        var response = mvc.perform(asManager(post("/api/fg-issue-requests/" + request + "/approve")))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertWarning(json.readTree(response.getContentAsString()), false);
        assertWarning(detail(request, true), false);
        assertStock(4, 1);
        addStock(product, 6);
        assertWarning(create(2), false); // 8 still pending + 2 new = 10; approved request is excluded.
    }

    @Test
    void anyShortItemWarnsForTheWholeRequest() throws Exception {
        long otherProduct = id("INSERT INTO products(category_id,name,unit,selling_price) "
                + "SELECT category_id,'Second FG product','piece',1 FROM products WHERE id=?", product);
        addStock(otherProduct, 1);
        var created = createItems("{\"productId\":" + product + ",\"quantity\":1},"
                + "{\"productId\":" + otherProduct + ",\"quantity\":2}");
        assertWarning(created, true);
        assertWarning(detail(created.get("id").asLong(), true), true);
        assertStock(10, 0);
    }

    @Test
    void missingCustomerOrderNoteIsRejectedWithoutCreatingARequest() throws Exception {
        assertInvalidNote(json.createObjectNode());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n\r "})
    void blankCustomerOrderNoteIsRejectedWithoutCreatingARequest(String note) throws Exception {
        assertInvalidNote(json.createObjectNode().put("note", note));
    }

    void assertInvalidNote(com.fasterxml.jackson.databind.node.ObjectNode payload) throws Exception {
        payload.putArray("items").addObject().put("productId", product).put("quantity", 1);
        var response = mvc.perform(asStaff(post("/api/fg-issue-requests"))
                .contentType("application/json").content(json.writeValueAsString(payload)))
                .andExpect(status().isBadRequest()).andReturn().getResponse();
        assertThat(json.readTree(response.getContentAsByteArray()).path("errors").path("note").asText())
                .isEqualTo("Vui lòng nhập thông tin khách hàng hoặc đơn hàng");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM finished_goods_issue_requests WHERE requested_by=?", Integer.class, staff))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM finished_goods_issue_request_items i "
                + "JOIN finished_goods_issue_requests r ON r.id=i.request_id WHERE r.requested_by=?", Integer.class, staff))
                .isZero();
        assertStock(10, 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Khách hàng ABC - Đơn DH001", "  Khách hàng ABC - Đơn DH001  "})
    void validCustomerOrderNoteIsSavedAndReturnedTrimmed(String note) throws Exception {
        var payload = json.createObjectNode().put("note", note);
        payload.putArray("items").addObject().put("productId", product).put("quantity", 1);
        var response = mvc.perform(asStaff(post("/api/fg-issue-requests"))
                .contentType("application/json").content(json.writeValueAsString(payload)))
                .andExpect(status().isOk()).andReturn().getResponse();
        var created = json.readTree(response.getContentAsByteArray());
        long request = created.get("id").asLong();
        assertThat(created.get("note").asText()).isEqualTo(note.trim());
        assertThat(detail(request, false).get("note").asText()).isEqualTo(note.trim());
        assertThat(jdbc.queryForObject("SELECT note FROM finished_goods_issue_requests WHERE id=?", String.class, request))
                .isEqualTo(note.trim());
        assertStock(10, 0);
    }
}
