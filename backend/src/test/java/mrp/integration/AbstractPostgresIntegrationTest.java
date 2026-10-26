package mrp.integration;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@SpringBootTest
@Testcontainers
public abstract class AbstractPostgresIntegrationTest {

    @Container
    public static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("mrp")
            .withUsername("postgres")
            .withPassword("secret")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(resolveMrpSqlPath()),
                    "/docker-entrypoint-initdb.d/01_mrp.sql"
            );

    private static String resolveMrpSqlPath() {
        Path p = Paths.get("..", "database", "mrp.sql").toAbsolutePath().normalize();
        if (!p.toFile().exists()) {
            p = Paths.get("database", "mrp.sql").toAbsolutePath().normalize();
        }
        return p.toString();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("app.jwt.secret", () -> "mrptest-very-secure-jwt-secret-key-at-least-256-bits-long-0000");
        registry.add("app.jwt.expiration-seconds", () -> "3600");
        registry.add("app.bootstrap-admin.username", () -> "admin");
        registry.add("app.bootstrap-admin.password", () -> "Admin@12345");
        registry.add("app.bootstrap-admin.full-name", () -> "Test Administrator");
    }
}
