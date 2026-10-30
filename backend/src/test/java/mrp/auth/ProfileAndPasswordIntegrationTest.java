package mrp.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import mrp.admin.dto.CreateUserRequest;
import mrp.admin.service.AdminUserService;
import mrp.user.entity.UserRole;
import mrp.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties = {"debug=false", "logging.level.root=WARN", "logging.level.org.hibernate.SQL=WARN"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProfileAndPasswordIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AdminUserService admin;
    @Autowired UserRepository users;
    static final String PASSWORD = "Original@12345";
    static final String NEW_PASSWORD = "Changed@12345";

    JsonNode call(MockHttpServletRequestBuilder builder, int expected) throws Exception {
        var response = mvc.perform(builder).andReturn().getResponse();
        assertEquals(expected, response.getStatus(), response.getContentAsString());
        return response.getContentAsByteArray().length == 0 ? json.nullNode() : json.readTree(response.getContentAsByteArray());
    }

    MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder builder, Object payload) throws Exception {
        return builder.contentType("application/json").content(json.writeValueAsBytes(payload));
    }

    String account(UserRole role) {
        String username = "profile" + UUID.randomUUID().toString().replace("-", "");
        admin.create(new CreateUserRequest(username, PASSWORD, role, "Before", "old@example.com", "0901234567", "Old address"));
        return username;
    }

    String login(String username, String password) throws Exception {
        return call(body(post("/api/auth/login"), Map.of("username", username, "password", password)), 200).get("accessToken").asText();
    }

    MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }

    Map<String, String> password(String current, String next, String confirm) {
        return Map.of("currentPassword", current, "newPassword", next, "confirmPassword", confirm);
    }

    @ParameterizedTest @EnumSource(UserRole.class)
    void profileUpdatesOnlyCurrentAccountAndPreservesIdentity(UserRole role) throws Exception {
        String username = account(role), other = account(role), token = login(username, PASSWORD);
        long otherId = users.findByUsername(other).orElseThrow().getId();
        var before = call(auth(get("/api/auth/me"), token), 200);
        var updated = call(body(auth(put("/api/auth/me"), token), Map.of(
                "fullName", "  Updated Name  ", "email", "new@example.com", "phone", "0987654321", "address", "New address",
                "userId", otherId, "role", "ADMIN", "username", other)), 200);
        assertEquals("Updated Name", updated.get("fullName").asText());
        assertEquals("new@example.com", updated.get("email").asText());
        assertEquals("0987654321", updated.get("phone").asText());
        assertEquals("New address", updated.get("address").asText());
        assertEquals(before.get("id"), updated.get("id"));
        assertEquals(before.get("profileId"), updated.get("profileId"));
        assertEquals(username, updated.get("username").asText());
        assertEquals(role.name(), updated.get("role").asText());
        assertEquals("Updated Name", call(auth(get("/api/auth/me"), token), 200).get("fullName").asText());
        assertEquals("Before", call(auth(get("/api/auth/me"), login(other, PASSWORD)), 200).get("fullName").asText());
    }

    @ParameterizedTest @EnumSource(UserRole.class)
    void passwordChangeRevokesAllOldTokensAndAllowsOnlyNewPassword(UserRole role) throws Exception {
        String username = account(role), other = account(role);
        String token = login(username, PASSWORD), secondSession = login(username, PASSWORD);
        call(body(auth(post("/api/auth/change-password"), token), password(PASSWORD, NEW_PASSWORD, NEW_PASSWORD)), 204);
        call(auth(get("/api/auth/me"), token), 401);
        call(auth(get("/api/auth/me"), secondSession), 401);
        call(body(post("/api/auth/login"), Map.of("username", username, "password", PASSWORD)), 401);
        String newToken = login(username, NEW_PASSWORD);
        call(auth(get("/api/auth/me"), newToken), 200);
        call(auth(get("/api/auth/me"), login(other, PASSWORD)), 200);
        assertNotEquals(NEW_PASSWORD, users.findByUsername(username).orElseThrow().getPasswordHash());
    }

    @Test void invalidPasswordChangesKeepSessionAndPassword() throws Exception {
        String username = account(UserRole.ADMIN), token = login(username, PASSWORD);
        for (var payload : java.util.List.of(
                password("wrong", NEW_PASSWORD, NEW_PASSWORD),
                password(PASSWORD, NEW_PASSWORD, "Mismatch@12345"),
                password(PASSWORD, PASSWORD, PASSWORD),
                password(PASSWORD, "short", "short"),
                password(PASSWORD, " ", " "),
                password(PASSWORD, "a".repeat(73), "a".repeat(73)),
                password(PASSWORD, "ệ".repeat(25), "ệ".repeat(25)))) {
            call(body(auth(post("/api/auth/change-password"), token), payload), 400);
            call(auth(get("/api/auth/me"), token), 200);
        }
        login(username, PASSWORD);
    }

    @Test void profileValidationAndClearingOptionalFields() throws Exception {
        String token = login(account(UserRole.STAFF), PASSWORD);
        for (var payload : java.util.List.of(
                Map.of("fullName", "   "), Map.of("fullName", "x".repeat(151)),
                Map.of("fullName", "Name", "email", "invalid"),
                Map.of("fullName", "Name", "phone", "1".repeat(21)),
                Map.of("fullName", "Name", "address", "x".repeat(256)))) {
            call(body(auth(put("/api/auth/me"), token), payload), 400);
        }
        var result = call(body(auth(put("/api/auth/me"), token), Map.of("fullName", "Name", "email", "", "phone", "", "address", "")), 200);
        assertTrue(result.get("email").isNull());
        assertTrue(result.get("phone").isNull());
        assertTrue(result.get("address").isNull());
    }

    @Test void anonymousRequestsAreRejected() throws Exception {
        call(body(put("/api/auth/me"), Map.of("fullName", "Name")), 401);
        call(body(post("/api/auth/change-password"), password(PASSWORD, NEW_PASSWORD, NEW_PASSWORD)), 401);
    }
}
