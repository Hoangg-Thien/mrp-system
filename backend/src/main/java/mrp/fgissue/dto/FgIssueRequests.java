package mrp.fgissue.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public class FgIssueRequests {

    public record CreateFgIssueRequest(
        @NotEmpty(message = "Danh sách sản phẩm không được để trống")
        @Valid
        List<FgIssueItemRequest> items,

        String note
    ) { }

    public record FgIssueItemRequest(
        @NotNull(message = "ID sản phẩm không được để trống")
        @Positive(message = "ID sản phẩm phải lớn hơn 0")
        Long productId,

        @NotNull(message = "Số lượng không được để trống")
        @Positive(message = "Số lượng phải lớn hơn 0")
        Integer quantity
    ) { }

    public record RejectRequest(
        @NotBlank(message = "Lý do từ chối không được để trống")
        String reason
    ) { }
}