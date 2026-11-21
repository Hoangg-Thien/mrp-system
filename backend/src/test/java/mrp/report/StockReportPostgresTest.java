package mrp.report;

import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"app.bootstrap-admin.username=", "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class StockReportPostgresTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("mrp_stock_report")
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
    long material, product, creator;

    long id(String sql, Object... args) {
        return jdbc.queryForObject(sql + " RETURNING id", Long.class, args);
    }

    @BeforeEach
    void stockHistory() {
        String suffix = UUID.randomUUID().toString();
        creator = id("INSERT INTO users(username,password_hash,role) VALUES (?,'test-only','WAREHOUSE_MANAGER')", suffix);
        long category = id("INSERT INTO categories(name) VALUES (?)", suffix);
        material = id("INSERT INTO materials(code,name,unit) VALUES (?,'Report material','kg')", suffix);
        product = id("INSERT INTO products(category_id,name,unit,selling_price) VALUES (?,'Report product','piece',1)", category);
        for (String column : new String[]{"material_id", "product_id"}) {
            long item = column.equals("material_id") ? material : product;
            movement(column, item, "ADJUST_IN", 20, "2026-10-31 23:59:59");
            movement(column, item, "ADJUST_IN", 5, "2026-11-01 00:00:00");
            movement(column, item, "ADJUST_OUT", 3, "2026-11-15 10:00:00");
            movement(column, item, "ADJUST_IN", 100, "2026-12-01 00:00:00");
        }
    }

    void movement(String column, long item, String type, int quantity, String timestamp) {
        jdbc.update("INSERT INTO stock_movements(" + column
                + ",movement_type,quantity,created_by,created_at,note) VALUES (?,?,?,?,?,'Report regression')",
                item, type, quantity, creator, Timestamp.valueOf(timestamp));
    }

    @ParameterizedTest
    @CsvSource({"MATERIAL,MONTH,11,ADMIN", "PRODUCT,MONTH,11,ADMIN",
            "MATERIAL,QUARTER,4,WAREHOUSE_MANAGER", "PRODUCT,QUARTER,4,WAREHOUSE_MANAGER",
            "MATERIAL,YEAR,1,ADMIN", "PRODUCT,YEAR,1,ADMIN"})
    void reportLoadsOnPostgres(String kind, String granularity, String period, String role) throws Exception {
        var response = mvc.perform(get("/api/reports/stock")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role)))
                .param("kind", kind).param("granularity", granularity)
                .param("year", "2026").param("period", period))
            .andExpect(status().isOk()).andReturn().getResponse();
        long expectedId = kind.equals("MATERIAL") ? material : product;
        var rows = json.readTree(response.getContentAsString());
        var row = java.util.stream.StreamSupport.stream(rows.spliterator(), false)
                .filter(item -> item.get("id").asLong() == expectedId).findFirst().orElseThrow();
        boolean monthly = granularity.equals("MONTH");
        assertThat(row.get("openingBalance").decimalValue()).isEqualByComparingTo(monthly ? "20" : "0");
        assertThat(row.get("adjustInQuantity").decimalValue()).isEqualByComparingTo(monthly ? "5" : "125");
        assertThat(row.get("adjustOutQuantity").decimalValue()).isEqualByComparingTo("3");
        assertThat(row.get("inQuantity").decimalValue()).isZero();
        assertThat(row.get("outQuantity").decimalValue()).isZero();
        assertThat(row.get("closingBalance").decimalValue()).isEqualByComparingTo(monthly ? "22" : "122");
    }
}
