package mrp.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties = {"debug=false", "logging.level.root=WARN", "logging.level.org.hibernate.SQL=WARN"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "admin", roles = "ADMIN")
class AdminFunctionsIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    JsonNode request(MockHttpServletRequestBuilder builder, int expected) throws Exception {
        var response = mvc.perform(builder).andReturn().getResponse();
        assertEquals(expected, response.getStatus(), response.getContentAsString());
        return response.getContentAsByteArray().length == 0 ? json.nullNode()
                : json.readTree(response.getContentAsByteArray());
    }

    MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder builder, Object value) throws Exception {
        return builder.contentType("application/json").content(json.writeValueAsBytes(value));
    }

    String unique() { return "qa" + UUID.randomUUID().toString().replace("-", ""); }

    long category(String name) throws Exception {
        return request(body(post("/api/categories"), Map.of("name", name)), 200).get("id").asLong();
    }

    Map<String, Object> catalogBody(String resource, String name, long categoryId) {
        return switch (resource) {
            case "categories" -> Map.of("name", name, "description", "QA");
            case "suppliers" -> Map.of("name", name, "phone", "0901234567", "email", "qa@example.com");
            case "products" -> Map.of("name", name, "categoryId", categoryId, "unit", "piece", "sellingPrice", 100);
            default -> Map.of("code", name, "name", name, "unit", "kg", "standardCost", 12.50);
        };
    }

    @ParameterizedTest
    @ValueSource(strings = {"categories", "suppliers", "products", "materials"})
    void catalogCreateReadSearchUpdateDelete(String resource) throws Exception {
        long parent = category(unique());
        String name = unique(), path = "/api/" + resource;
        long id = request(body(post(path), catalogBody(resource, name, parent)), 200).get("id").asLong();
        assertEquals(name, request(get(path + "/" + id), 200).get("name").asText());
        var list = request(get(path).param("keyword", name).param("size", "1").param("sort", "name").param("direction", "desc"), 200);
        assertEquals(1, list.get("totalElements").asInt());
        assertEquals(id, list.get("content").get(0).get("id").asLong());
        assertEquals(0, request(get(path).param("keyword", name).param("page", "1").param("size", "1"), 200).get("content").size());
        assertEquals(name + "edit", request(body(put(path + "/" + id), catalogBody(resource, name + "edit", parent)), 200).get("name").asText());
        request(delete(path + "/" + id), 204);
        request(get(path + "/" + id), 404);
    }

    @ParameterizedTest
    @ValueSource(strings = {"products", "materials"})
    void activationAndInactiveFiltering(String resource) throws Exception {
        String name = unique(), path = "/api/" + resource;
        long id = request(body(post(path), catalogBody(resource, name, category(unique()))), 200).get("id").asLong();
        request(post(path + "/" + id + "/deactivate"), 200);
        assertEquals(1, request(get(path).param("keyword", name).param("active", "false"), 200).get("totalElements").asInt());
        assertEquals(0, request(get(path).param("keyword", name).param("active", "true"), 200).get("totalElements").asInt());
        request(post(path + "/" + id + "/activate"), 200);
        assertEquals(1, request(get(path).param("keyword", name).param("active", "true"), 200).get("totalElements").asInt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"categories", "suppliers", "products", "materials"})
    void catalogInvalidPayloadRejected(String resource) throws Exception {
        request(body(post("/api/" + resource), Map.of("name", "")), 400);
    }

    @Test void productAdvancedFiltersAndCategoryReferenceGuard() throws Exception {
        long parent = category(unique());
        String name = unique();
        request(body(post("/api/products"), catalogBody("products", name, parent)), 200);
        assertEquals(1, request(get("/api/products").param("categoryId", "" + parent).param("minPrice", "99").param("maxPrice", "101"), 200).get("totalElements").asInt());
        assertEquals(0, request(get("/api/products").param("categoryId", "" + parent).param("minPrice", "101"), 200).get("totalElements").asInt());
        request(delete("/api/categories/" + parent), 409);
    }

    @Test void supplierAdvancedFilters() throws Exception {
        String name = unique();
        request(body(post("/api/suppliers"), catalogBody("suppliers", name, 0)), 200);
        assertEquals(1, request(get("/api/suppliers").param("name", name).param("phone", "090123").param("email", "qa@example.com"), 200).get("totalElements").asInt());
        assertEquals(0, request(get("/api/suppliers").param("name", name).param("phone", "99999999"), 200).get("totalElements").asInt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "WAREHOUSE_MANAGER", "STAFF"})
    void accountCreateFilterLockUnlockChangeRoleAndLogin(String role) throws Exception {
        String name = unique(), password = "QaTest@12345";
        var payload = Map.of("username", name, "password", password, "role", role, "fullName", name, "email", "qa@example.com");
        var created = request(body(post("/api/admin/users"), payload), 200);
        long id = created.get("id").asLong();
        assertEquals(role, created.get("role").asText());
        assertTrue(created.get("profileId").asLong() > 0);
        request(body(post("/api/admin/users"), payload), 409);
        assertEquals(1, request(get("/api/admin/users").param("keyword", name).param("role", role).param("active", "true"), 200).get("totalElements").asInt());
        var login = Map.of("username", name, "password", password);
        var token = request(body(post("/api/auth/login"), login), 200).get("accessToken").asText();
        assertFalse(token.isBlank());
        request(post("/api/admin/users/" + id + "/lock"), 200);
        request(body(post("/api/auth/login"), login), 401);
        request(post("/api/admin/users/" + id + "/unlock"), 200);
        request(body(post("/api/auth/login"), login), 200);
        String newRole = role.equals("STAFF") ? "WAREHOUSE_MANAGER" : "STAFF";
        assertEquals(newRole, request(body(post("/api/admin/users/" + id + "/role"), Map.of("role", newRole)), 200).get("role").asText());
        assertEquals(newRole, request(body(post("/api/auth/login"), login), 200).get("user").get("role").asText());
    }

    @Test void dashboardAndProfileReads() throws Exception {
        for (String path : new String[]{"/api/admin/users", "/api/products", "/api/materials", "/api/suppliers", "/api/categories", "/api/staff", "/api/stock-movements", "/api/auth/me"}) {
            request(get(path), 200);
        }
        request(get("/api/production-requests"), 403);
        request(get("/api/production-requests/1"), 403);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MONTH", "QUARTER", "YEAR"})
    void adminCanReadReports(String granularity) throws Exception {
        for (String kind : new String[]{"MATERIAL", "PRODUCT"}) {
            assertTrue(request(get("/api/reports/stock").param("kind", kind).param("granularity", granularity).param("year", "2026").param("period", "1"), 200).isArray());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"STAFF", "WAREHOUSE_MANAGER"})
    void otherRolesCannotManageAdminResources(String role) throws Exception {
        request(get("/api/admin/users").with(user("other").roles(role)), 403);
        for (String resource : new String[]{"categories", "suppliers", "products", "materials"}) {
            request(body(post("/api/" + resource).with(user("other").roles(role)), catalogBody(resource, unique(), 1)), 403);
        }
    }
}
