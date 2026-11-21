package mrp;

import jakarta.validation.Validation;
import java.util.Collections;
import java.util.stream.Stream;
import mrp.bom.dto.BomRequests;
import mrp.fgissue.dto.FgIssueRequests;
import mrp.production.dto.ProductionRequests;
import mrp.purchase.dto.PurchaseRequests;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class RequestItemValidationTest {
    static Stream<Object> requestsWithNullItems() {
        return Stream.of(
                new BomRequests.CreateBomRequest(1L, Collections.singletonList(null)),
                new PurchaseRequests.PurchaseReceiptRequest(1L, Collections.singletonList(null)),
                new ProductionRequests.CreateMaterialIssueRequest(1L, Collections.singletonList(null), "Test"),
                new FgIssueRequests.CreateFgIssueRequest(Collections.singletonList(null), "Test"));
    }

    @ParameterizedTest
    @MethodSource("requestsWithNullItems")
    void nullLineIsRejectedBeforeServiceExecution(Object request) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request)).isNotEmpty();
        }
    }
}
