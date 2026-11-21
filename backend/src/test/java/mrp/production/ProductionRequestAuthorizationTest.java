package mrp.production;

import mrp.production.controller.ProductionRequestController;
import mrp.production.service.ProductionService;
import mrp.security.CurrentUserResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ProductionRequestAuthorizationTest {
    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean ProductionService service() { return mock(ProductionService.class); }
        @Bean CurrentUserResolver resolver() { return mock(CurrentUserResolver.class); }
        @Bean ProductionRequestController controller(ProductionService service, CurrentUserResolver resolver) {
            return new ProductionRequestController(service, resolver);
        }
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "WAREHOUSE_MANAGER", "STAFF"})
    void productionRequestReadsRespectRoles(String role) {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var auth = new UsernamePasswordAuthenticationToken("user", "unused",
                    AuthorityUtils.createAuthorityList("ROLE_" + role));
            SecurityContextHolder.getContext().setAuthentication(auth);
            var controller = context.getBean(ProductionRequestController.class);
            if (role.equals("ADMIN")) {
                assertThrows(AccessDeniedException.class,
                        () -> controller.list(null, null, null, 0, 20, "id", "desc", auth));
                assertThrows(AccessDeniedException.class, () -> controller.detail(1L, auth));
                verifyNoInteractions(context.getBean(ProductionService.class),
                        context.getBean(CurrentUserResolver.class));
            } else {
                assertDoesNotThrow(() -> controller.list(null, null, null, 0, 20, "id", "desc", auth));
                assertDoesNotThrow(() -> controller.detail(1L, auth));
            }
        }
    }
}
