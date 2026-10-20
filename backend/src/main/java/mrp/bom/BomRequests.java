package mrp.bom;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;

public class BomRequests {

    public record CreateBomRequest(
        @NotNull(message = "ID sản phẩm không được để trống")
        @Positive(message = "ID sản phẩm phải lớn hơn 0")
        Long productId,

        @NotEmpty(message = "Danh sách nguyên vật liệu không được để trống")
        @Valid
        List<BomItemRequest> items
    ) {}

    public record BomItemRequest(
        @NotNull(message = "ID nguyên vật liệu không được để trống")
        @Positive(message = "ID nguyên vật liệu phải lớn hơn 0")
        Long materialId,

        @NotNull(message = "Số lượng không được để trống")
        @Positive(message = "Số lượng phải lớn hơn 0")
        @Digits(integer = 12, fraction = 3, message = "Số lượng tối đa 12 số nguyên và 3 số thập phân")
        BigDecimal quantity
    ) {}
}
