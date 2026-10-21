package mrp.production.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;

public class ProductionRequests {

    public record CreateProductionRequest(
        @NotNull(message = "ID sản phẩm không được để trống")
        @Positive(message = "ID sản phẩm phải lớn hơn 0")
        Long productId,

        @NotNull(message = "Số lượng không được để trống")
        @Positive(message = "Số lượng phải lớn hơn 0")
        Integer quantity,

        @NotNull(message = "Nhân viên được giao không được để trống")
        @Positive(message = "ID nhân viên phải lớn hơn 0")
        Long assignedStaffId,

        String note
    ) { }

    public record CreateMaterialIssueRequest(
        @NotNull(message = "ID yêu cầu sản xuất không được để trống")
        @Positive(message = "ID yêu cầu sản xuất phải lớn hơn 0")
        Long productionRequestId,

        @NotEmpty(message = "Danh sách nguyên vật liệu không được để trống")
        @Valid
        List<IssueItemRequest> items,

        String note
    ) { }

    public record IssueItemRequest(
        @NotNull(message = "ID nguyên vật liệu không được để trống")
        @Positive(message = "ID nguyên vật liệu phải lớn hơn 0")
        Long materialId,

        @NotNull(message = "Số lượng không được để trống")
        @Positive(message = "Số lượng phải lớn hơn 0")
        @Digits(integer = 12, fraction = 3, message = "Số lượng tối đa 12 số nguyên và 3 số thập phân")
        BigDecimal quantity
    ) { }

    public record RejectRequest(
        @NotBlank(message = "Lý do từ chối không được để trống")
        String reason
    ) { }

    public record CreateProductionExecutionRequest(
        @NotNull(message = "ID yêu cầu sản xuất không được để trống")
        @Positive(message = "ID yêu cầu sản xuất phải lớn hơn 0")
        Long productionRequestId,

        @NotNull(message = "Số lượng không được để trống")
        @Positive(message = "Số lượng phải lớn hơn 0")
        Integer quantity,

        String note
    ) { }
}