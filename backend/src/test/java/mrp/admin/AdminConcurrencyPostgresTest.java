package mrp.admin;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import mrp.admin.service.AdminUserService;
import mrp.common.exception.BusinessException;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** Requires a disposable database initialized with database/mrp.sql. */
@SpringBootTest(properties = {"app.bootstrap-admin.username=", "spring.jpa.hibernate.ddl-auto=validate"})
@EnabledIfEnvironmentVariable(named = "MRP_REGRESSION_DB_URL", matches = "jdbc:postgresql:.*")
class AdminConcurrencyPostgresTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("MRP_REGRESSION_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("MRP_REGRESSION_DB_USER"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("MRP_REGRESSION_DB_PASSWORD", ""));
    }

    @Autowired AdminUserService service;
    @Autowired JdbcTemplate jdbc;

    long admin(String username) {
        Long id = jdbc.queryForObject("insert into users(username,password_hash,role) values (?,'test','ADMIN') returning id", Long.class, username);
        jdbc.update("insert into admins(user_id,full_name) values (?,'Audit admin')", id);
        return id;
    }

    @RepeatedTest(5)
    void concurrentLocksKeepAnActiveAdministrator() throws Exception {
        jdbc.update("update users set is_active=false where role='ADMIN'");
        String firstName = "a" + UUID.randomUUID(), secondName = "b" + UUID.randomUUID();
        long first = admin(firstName), second = admin(secondName);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(() -> lock(second, firstName, start));
            var two = pool.submit(() -> lock(first, secondName, start));
            start.countDown();
            assertThat(List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS))).contains(true);
        }
        assertThat(jdbc.queryForObject("select count(*) from users where role='ADMIN' and is_active", Long.class)).isGreaterThanOrEqualTo(1L);
    }

    boolean lock(long id, String actor, CountDownLatch start) throws Exception {
        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
        try {
            service.lock(id, new UsernamePasswordAuthenticationToken(actor, "unused"));
            return true;
        } catch (BusinessException expected) {
            return false;
        }
    }
}
