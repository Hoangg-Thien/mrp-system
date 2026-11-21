package mrp.production;

import java.math.BigDecimal;
import java.util.List;
import mrp.common.exception.GlobalExceptionHandler;
import mrp.production.controller.MrpPlanController;
import mrp.production.service.MrpCalculator;
import mrp.production.service.ProductionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercises Spring MVC request binding and the real advice, without a database. */
class MrpPlanControllerParameterTest {
    ProductionService service;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(ProductionService.class);
        mvc = MockMvcBuilders.standaloneSetup(new MrpPlanController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @ParameterizedTest
    @CsvSource({
            "/api/mrp/plan?productId=1, quantity",
            "/api/mrp/plan?quantity=5, productId",
            "/api/mrp/plan, productId",
            "/api/mrp/plan?productId=1&quantity=, quantity",
            "/api/mrp/plan?productId=&quantity=5, productId"
    })
    void missingOrEmptyParameterReturnsBadRequestAndNamesTheField(String path, String missing) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Thiếu tham số bắt buộc: " + missing));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"productId", "quantity"})
    void invalidNumberStillReturnsBadRequest(String parameter) throws Exception {
        mvc.perform(get("/api/mrp/plan")
                        .param("productId", parameter.equals("productId") ? "abc" : "1")
                        .param("quantity", parameter.equals("quantity") ? "abc" : "5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Tham số không hợp lệ"));
        verifyNoInteractions(service);
    }

    @Test
    void completeParametersReachThePlanningService() throws Exception {
        when(service.getPlan(1L, 5)).thenReturn(new MrpCalculator.PlanResult(
                1L, 5, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 5, List.of()));
        mvc.perform(get("/api/mrp/plan").param("productId", "1").param("quantity", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(1))
                .andExpect(jsonPath("$.targetQuantity").value(5))
                .andExpect(jsonPath("$.toProduce").value(5));
        verify(service).getPlan(1L, 5);
    }

    @Test
    void actualServerFailureStillReturnsInternalServerError() throws Exception {
        when(service.getPlan(1L, 5)).thenThrow(new IllegalStateException("test-only internal failure"));
        mvc.perform(get("/api/mrp/plan").param("productId", "1").param("quantity", "5"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Đã xảy ra lỗi không mong muốn"));
    }
}
