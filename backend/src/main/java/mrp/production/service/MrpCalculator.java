package mrp.production.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public final class MrpCalculator {

    private MrpCalculator() { }

    public record MaterialDemandInput(
        Long materialId,
        String materialCode,
        String materialName,
        String unit,
        BigDecimal bomQuantity,   // Số lượng NVL cho 1 đơn vị thành phẩm
        BigDecimal onHand,        // Tồn kho hiện tại của NVL
        BigDecimal committed      // Đã cam kết cho các production_request mở khác
    ) { }

    public record MaterialPlanResult(
        Long materialId,
        String materialCode,
        String materialName,
        String unit,
        BigDecimal bomQuantity,
        BigDecimal gross,         // bomQuantity * toProduce
        BigDecimal onHand,        // Tồn kho NVL
        BigDecimal committed,     // Đã cam kết
        BigDecimal usable,        // max(0, onHand - committed)
        BigDecimal shortage       // max(0, gross - usable) (số lượng đề nghị nhập thêm)
    ) { }

    public record PlanResult(
        Long productId,
        int targetQuantity,
        BigDecimal fgOnHand,
        BigDecimal fgReserved,
        BigDecimal fgIncoming,
        BigDecimal available,     // fgOnHand - fgReserved + fgIncoming
        int toProduce,            // max(0, targetQuantity - available)
        List<MaterialPlanResult> materials
    ) { }

    public static PlanResult calculate(
        Long productId,
        int targetQuantity,
        BigDecimal fgOnHand,
        BigDecimal fgReserved,
        BigDecimal fgIncoming,
        List<MaterialDemandInput> materials
    ) {
        BigDecimal onHand = fgOnHand != null ? fgOnHand : BigDecimal.ZERO;
        BigDecimal reserved = fgReserved != null ? fgReserved : BigDecimal.ZERO;
        BigDecimal incoming = fgIncoming != null ? fgIncoming : BigDecimal.ZERO;

        BigDecimal available = onHand.subtract(reserved).add(incoming);
        BigDecimal target = BigDecimal.valueOf(targetQuantity);
        BigDecimal diff = target.subtract(available);

        int toProduce = 0;
        if (diff.compareTo(BigDecimal.ZERO) > 0) {
            toProduce = diff.setScale(0, RoundingMode.CEILING).intValue();
        }

        BigDecimal toProduceBd = BigDecimal.valueOf(toProduce);
        List<MaterialPlanResult> matResults = new ArrayList<>();

        if (materials != null) {
            for (MaterialDemandInput mat : materials) {
                BigDecimal bomQty = mat.bomQuantity() != null ? mat.bomQuantity() : BigDecimal.ZERO;
                BigDecimal matOnHand = mat.onHand() != null ? mat.onHand() : BigDecimal.ZERO;
                BigDecimal matCommitted = mat.committed() != null ? mat.committed() : BigDecimal.ZERO;

                BigDecimal gross = bomQty.multiply(toProduceBd).setScale(3, RoundingMode.HALF_UP);
                BigDecimal usable = matOnHand.subtract(matCommitted).max(BigDecimal.ZERO).setScale(3, RoundingMode.HALF_UP);
                BigDecimal shortage = gross.subtract(usable).max(BigDecimal.ZERO).setScale(3, RoundingMode.HALF_UP);

                matResults.add(new MaterialPlanResult(
                    mat.materialId(),
                    mat.materialCode(),
                    mat.materialName(),
                    mat.unit(),
                    bomQty,
                    gross,
                    matOnHand,
                    matCommitted,
                    usable,
                    shortage
                ));
            }
        }

        return new PlanResult(
            productId,
            targetQuantity,
            onHand,
            reserved,
            incoming,
            available,
            toProduce,
            matResults
        );
    }
}