package mrp.production;


import mrp.production.service.MrpCalculator;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class MrpCalculatorTest {

    @Test
    void mrp01_tableExampleNumbers() {
        // Ví dụ bàn (BOM 1 mặt + 4 chân):
        // muốn có sẵn: 10 bàn; tồn thành phẩm: 2, phiếu xuất PENDING: 1, PR mở còn: 3
        // -> available = 2 - 1 + 3 = 4, toProduce = 6;
        // NVL: mặt 6, chân 24. Tồn mặt 5, chân 30, không có cam kết -> thiếu mặt 1, chân 0.
        Long productId = 10L;
        int targetQty = 10;
        BigDecimal fgOnHand = new BigDecimal("2.000");
        BigDecimal fgReserved = new BigDecimal("1.000");
        BigDecimal fgIncoming = new BigDecimal("3.000");

        List<MrpCalculator.MaterialDemandInput> materials = List.of(
            new MrpCalculator.MaterialDemandInput(101L, "MAT-01", "Mặt bàn", "tấm", new BigDecimal("1.000"), new BigDecimal("5.000"), BigDecimal.ZERO),
            new MrpCalculator.MaterialDemandInput(102L, "CHAN-01", "Chân bàn", "cái", new BigDecimal("4.000"), new BigDecimal("30.000"), BigDecimal.ZERO)
        );

        MrpCalculator.PlanResult result = MrpCalculator.calculate(productId, targetQty, fgOnHand, fgReserved, fgIncoming, materials);

        assertEquals(new BigDecimal("4.000"), result.available());
        assertEquals(6, result.toProduce());

        MrpCalculator.MaterialPlanResult mat = result.materials().get(0);
        assertEquals(new BigDecimal("6.000"), mat.gross());
        assertEquals(new BigDecimal("5.000"), mat.usable());
        assertEquals(new BigDecimal("1.000"), mat.shortage());

        MrpCalculator.MaterialPlanResult chan = result.materials().get(1);
        assertEquals(new BigDecimal("24.000"), chan.gross());
        assertEquals(new BigDecimal("30.000"), chan.usable());
        assertEquals(new BigDecimal("0.000"), chan.shortage());
    }

    @Test
    void mrp01_targetLessThanAvailableYieldsZeroToProduce() {
        Long productId = 10L;
        int targetQty = 3;
        BigDecimal fgOnHand = new BigDecimal("2.000");
        BigDecimal fgReserved = new BigDecimal("1.000");
        BigDecimal fgIncoming = new BigDecimal("3.000"); // available = 4

        List<MrpCalculator.MaterialDemandInput> materials = List.of(
            new MrpCalculator.MaterialDemandInput(101L, "MAT-01", "Mặt bàn", "tấm", new BigDecimal("1.000"), new BigDecimal("5.000"), BigDecimal.ZERO)
        );

        MrpCalculator.PlanResult result = MrpCalculator.calculate(productId, targetQty, fgOnHand, fgReserved, fgIncoming, materials);

        assertEquals(0, result.toProduce());
        assertEquals(new BigDecimal("0.000"), result.materials().get(0).gross());
        assertEquals(new BigDecimal("0.000"), result.materials().get(0).shortage());
    }

    @Test
    void mrp01b_committedReducesUsableAndIncreasesShortage() {
        // Tồn mặt bàn = 5, nhưng đã cam kết = 3 -> usable = 2.
        // Cần gross = 6 -> thiếu = 6 - 2 = 4.
        Long productId = 10L;
        int targetQty = 10;
        BigDecimal fgOnHand = new BigDecimal("4.000");
        BigDecimal fgReserved = BigDecimal.ZERO;
        BigDecimal fgIncoming = BigDecimal.ZERO; // available = 4, toProduce = 6

        List<MrpCalculator.MaterialDemandInput> materials = List.of(
            new MrpCalculator.MaterialDemandInput(101L, "MAT-01", "Mặt bàn", "tấm", new BigDecimal("1.000"), new BigDecimal("5.000"), new BigDecimal("3.000"))
        );

        MrpCalculator.PlanResult result = MrpCalculator.calculate(productId, targetQty, fgOnHand, fgReserved, fgIncoming, materials);

        assertEquals(6, result.toProduce());
        MrpCalculator.MaterialPlanResult mat = result.materials().get(0);
        assertEquals(new BigDecimal("6.000"), mat.gross());
        assertEquals(new BigDecimal("2.000"), mat.usable());
        assertEquals(new BigDecimal("4.000"), mat.shortage());
    }
}