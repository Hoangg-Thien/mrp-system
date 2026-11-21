package mrp.bom.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class BomViews {

    public record BomView(
        Long id,
        Long productId,
        String productName,
        String productUnit,
        Integer version,
        boolean active,
        LocalDateTime createdAt,
        int itemCount
    ) {}

    public record BomDetailView(
        Long id,
        Long productId,
        String productName,
        String productUnit,
        Integer version,
        boolean active,
        LocalDateTime createdAt,
        List<BomItemView> items
    ) {}

    public record BomItemView(
        Long id,
        Long materialId,
        String materialCode,
        String materialName,
        String materialUnit,
        BigDecimal quantity
    ) {}
}