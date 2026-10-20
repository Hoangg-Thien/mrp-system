package mrp.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StockReportIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StockReportService reportService;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUpData() {
        jdbc.execute("DELETE FROM stock_movements");
        jdbc.execute("DELETE FROM inventories");
        jdbc.execute("DELETE FROM materials");
        jdbc.execute("DELETE FROM products");
        jdbc.execute("DELETE FROM categories");
        jdbc.execute("DELETE FROM users");

        // 1. Dữ liệu danh mục & User
        jdbc.update("INSERT INTO categories (id, name, description) VALUES (1, 'Nội thất', 'Bàn ghế')");
        jdbc.update("INSERT INTO users (id, username, password_hash, role, is_active, created_at) VALUES (1, 'admin1', 'hash', 'ADMIN', true, CURRENT_TIMESTAMP)");

        // 2. Vật tư M1, M2
        jdbc.update("INSERT INTO materials (id, code, name, unit, standard_cost, is_active, created_at) VALUES (1, 'MAT01', 'Gỗ sồi', 'm3', 100000, true, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO materials (id, code, name, unit, standard_cost, is_active, created_at) VALUES (2, 'MAT02', 'Đinh thép', 'kg', 20000, true, CURRENT_TIMESTAMP)");

        // 3. Sản phẩm P1
        jdbc.update("INSERT INTO products (id, category_id, name, description, unit, selling_price, is_active, created_at) VALUES (1, 1, 'Bàn ăn', 'Bàn gỗ', 'cái', 500000, true, CURRENT_TIMESTAMP)");

        // 4. Các biến động kho M1
        // - Tháng 8: IN 100
        insertMovement(1L, null, "IN", 100.0, LocalDateTime.of(2026, 8, 15, 10, 0, 0));
        // - Tháng 9: OUT 30 (tồn cuối tháng 9 = 70)
        insertMovement(1L, null, "OUT", 30.0, LocalDateTime.of(2026, 9, 20, 14, 0, 0));
        // - Đúng 00:00 ngày 01/10/2026: IN 50 (RPT-02: thuộc tháng 10, không thuộc tháng 9)
        insertMovement(1L, null, "IN", 50.0, LocalDateTime.of(2026, 10, 1, 0, 0, 0));
        // - Tháng 10: ADJUST_IN 5, OUT 20, ADJUST_OUT 2
        insertMovement(1L, null, "ADJUST_IN", 5.0, LocalDateTime.of(2026, 10, 10, 9, 0, 0));
        insertMovement(1L, null, "OUT", 20.0, LocalDateTime.of(2026, 10, 15, 16, 0, 0));
        insertMovement(1L, null, "ADJUST_OUT", 2.0, LocalDateTime.of(2026, 10, 25, 11, 0, 0));
        // - Tháng 11: IN 40
        insertMovement(1L, null, "IN", 40.0, LocalDateTime.of(2026, 11, 5, 10, 0, 0));

        // 5. Biến động kho M2: chỉ phát sinh trong tháng 9 (IN 10), tháng 10 không phát sinh
        insertMovement(2L, null, "IN", 10.0, LocalDateTime.of(2026, 9, 1, 10, 0, 0));

        // 6. Biến động sản phẩm P1: tháng 10 IN 10, OUT 3
        insertMovement(null, 1L, "IN", 10.0, LocalDateTime.of(2026, 10, 5, 8, 0, 0));
        insertMovement(null, 1L, "OUT", 3.0, LocalDateTime.of(2026, 10, 20, 15, 0, 0));

        // 7. Tồn kho inventories hiện tại (tương đương kết quả trigger DB)
        // M1: 100 - 30 + 50 + 5 - 20 - 2 + 40 = 143.000
        jdbc.update("INSERT INTO inventories (material_id, quantity, updated_at) VALUES (1, 143.000, CURRENT_TIMESTAMP)");
        // M2: 10.000
        jdbc.update("INSERT INTO inventories (material_id, quantity, updated_at) VALUES (2, 10.000, CURRENT_TIMESTAMP)");
        // P1: 10 - 3 = 7.000
        jdbc.update("INSERT INTO inventories (product_id, quantity, updated_at) VALUES (1, 7.000, CURRENT_TIMESTAMP)");
    }

    private void insertMovement(Long matId, Long prodId, String type, double qty, LocalDateTime time) {
        jdbc.update("""
            INSERT INTO stock_movements (material_id, product_id, movement_type, quantity, created_by, created_at, note)
            VALUES (?, ?, ?, ?, 1, ?, 'Test movement')
            """, matId, prodId, type, BigDecimal.valueOf(qty), Timestamp.valueOf(time));
    }

    // =========================================================================
    // RPT-01: BÁO CÁO THÁNG / QUÝ / NĂM KHỚP TÍNH TAY
    // =========================================================================

    @Test
    void rpt01_monthlyReport_matchesExactCalculations() {
        List<StockReportItemView> report = reportService.getStockReport(
            StockReportKind.MATERIAL, ReportGranularity.MONTH, 2026, 10
        );

        assertEquals(2, report.size());

        // M1 (Gỗ sồi): Tồn đầu 70, Nhập 50, Điều chỉnh tăng 5, Xuất 20, Điều chỉnh giảm 2, Tồn cuối 103
        StockReportItemView m1 = report.stream().filter(r -> r.id().equals(1L)).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("70.000").compareTo(m1.openingBalance()));
        assertEquals(0, new BigDecimal("50.000").compareTo(m1.inQuantity()));
        assertEquals(0, new BigDecimal("5.000").compareTo(m1.adjustInQuantity()));
        assertEquals(0, new BigDecimal("20.000").compareTo(m1.outQuantity()));
        assertEquals(0, new BigDecimal("2.000").compareTo(m1.adjustOutQuantity()));
        assertEquals(0, new BigDecimal("103.000").compareTo(m1.closingBalance()));

        // M2 (Đinh thép): Không phát sinh trong tháng 10 nhưng có tồn đầu 10 -> vẫn xuất hiện!
        StockReportItemView m2 = report.stream().filter(r -> r.id().equals(2L)).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("10.000").compareTo(m2.openingBalance()));
        assertEquals(0, BigDecimal.ZERO.compareTo(m2.inQuantity()));
        assertEquals(0, BigDecimal.ZERO.compareTo(m2.outQuantity()));
        assertEquals(0, BigDecimal.ZERO.compareTo(m2.adjustInQuantity()));
        assertEquals(0, BigDecimal.ZERO.compareTo(m2.adjustOutQuantity()));
        assertEquals(0, new BigDecimal("10.000").compareTo(m2.closingBalance()));
    }

    @Test
    void rpt01_quarterlyReport_matchesCalculations() {
        // Quý 4/2026 (Tháng 10 + 11 + 12):
        // M1:
        // Tồn đầu quý 4 (trước 01/10): 70
        // Trong quý 4: IN 50 + 40 = 90, ADJUST_IN 5, OUT 20, ADJUST_OUT 2
        // Tồn cuối quý 4: 70 + 90 + 5 - 20 - 2 = 143
        List<StockReportItemView> report = reportService.getStockReport(
            StockReportKind.MATERIAL, ReportGranularity.QUARTER, 2026, 4
        );

        StockReportItemView m1 = report.stream().filter(r -> r.id().equals(1L)).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("70.000").compareTo(m1.openingBalance()));
        assertEquals(0, new BigDecimal("90.000").compareTo(m1.inQuantity()));
        assertEquals(0, new BigDecimal("5.000").compareTo(m1.adjustInQuantity()));
        assertEquals(0, new BigDecimal("20.000").compareTo(m1.outQuantity()));
        assertEquals(0, new BigDecimal("2.000").compareTo(m1.adjustOutQuantity()));
        assertEquals(0, new BigDecimal("143.000").compareTo(m1.closingBalance()));
    }

    @Test
    void rpt01_yearlyReport_matchesCalculations() {
        // Năm 2026:
        // Tồn đầu năm: 0
        // M1: IN 100 + 50 + 40 = 190, ADJUST_IN 5, OUT 30 + 20 = 50, ADJUST_OUT 2
        // Tồn cuối năm 2026: 0 + 190 + 5 - 50 - 2 = 143
        List<StockReportItemView> report = reportService.getStockReport(
            StockReportKind.MATERIAL, ReportGranularity.YEAR, 2026, null
        );

        StockReportItemView m1 = report.stream().filter(r -> r.id().equals(1L)).findFirst().orElseThrow();
        assertEquals(0, BigDecimal.ZERO.compareTo(m1.openingBalance()));
        assertEquals(0, new BigDecimal("190.000").compareTo(m1.inQuantity()));
        assertEquals(0, new BigDecimal("5.000").compareTo(m1.adjustInQuantity()));
        assertEquals(0, new BigDecimal("50.000").compareTo(m1.outQuantity()));
        assertEquals(0, new BigDecimal("2.000").compareTo(m1.adjustOutQuantity()));
        assertEquals(0, new BigDecimal("143.000").compareTo(m1.closingBalance()));
    }

    // =========================================================================
    // RPT-02: MOVEMENT ĐÚNG 00:00 NGÀY ĐẦU KỲ THUỘC KỲ ĐÓ, KHÔNG THUỘC KỲ TRƯỚC
    // =========================================================================

    @Test
    void rpt02_boundaryMovementAtMidnight_belongsToNewPeriod() {
        // Movement 50.000 phát sinh đúng 2026-10-01 00:00:00:
        // Khi xem tháng 9 (01/09 -> 01/10): tồn cuối tháng 9 = 70.000, không bao gồm 50.000
        List<StockReportItemView> septReport = reportService.getStockReport(
            StockReportKind.MATERIAL, ReportGranularity.MONTH, 2026, 9
        );
        StockReportItemView m1Sept = septReport.stream().filter(r -> r.id().equals(1L)).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("70.000").compareTo(m1Sept.closingBalance()));

        // Khi xem tháng 10 (01/10 -> 01/11): tồn đầu tháng 10 = 70.000, và 50.000 nằm trong inQuantity tháng 10
        List<StockReportItemView> octReport = reportService.getStockReport(
            StockReportKind.MATERIAL, ReportGranularity.MONTH, 2026, 10
        );
        StockReportItemView m1Oct = octReport.stream().filter(r -> r.id().equals(1L)).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("70.000").compareTo(m1Oct.openingBalance()));
        assertEquals(0, new BigDecimal("50.000").compareTo(m1Oct.inQuantity()));
    }

    // =========================================================================
    // RPT-03: TỒN CUỐI KỲ HIỆN TẠI = TỒN THỰC (INVENTORIES.QUANTITY)
    // =========================================================================

    @Test
    void rpt03_closingBalance_equalsRealInventoryQuantity() {
        List<StockReportItemView> report = reportService.getStockReport(
            StockReportKind.MATERIAL, ReportGranularity.YEAR, 2026, null
        );

        for (StockReportItemView item : report) {
            BigDecimal inventoryQty = jdbc.queryForObject(
                "SELECT quantity FROM inventories WHERE material_id = ?",
                BigDecimal.class, item.id()
            );
            assertNotNull(inventoryQty);
            assertEquals(0, inventoryQty.compareTo(item.closingBalance()),
                "Tồn cuối kỳ của vật tư #" + item.id() + " phải bằng tồn thực trong bảng inventories");
        }
    }

    // =========================================================================
    // RPT-04: PHÂN QUYỀN VÀ KIỂM TRA THAM SỐ
    // =========================================================================

    @Test
    @WithMockUser(roles = "STAFF")
    void rpt04_staffRole_forbidden403() throws Exception {
        mockMvc.perform(get("/api/reports/stock")
                .param("kind", "MATERIAL")
                .param("granularity", "MONTH")
                .param("year", "2026")
                .param("period", "10"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "WAREHOUSE_MANAGER")
    void rpt04_warehouseManagerRole_success200() throws Exception {
        mockMvc.perform(get("/api/reports/stock")
                .param("kind", "MATERIAL")
                .param("granularity", "MONTH")
                .param("year", "2026")
                .param("period", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void rpt04_invalidParameters_badRequest400() throws Exception {
        // Tháng 13 -> 400
        mockMvc.perform(get("/api/reports/stock")
                .param("kind", "MATERIAL")
                .param("granularity", "MONTH")
                .param("year", "2026")
                .param("period", "13"))
            .andExpect(status().isBadRequest());

        // Quý 5 -> 400
        mockMvc.perform(get("/api/reports/stock")
                .param("kind", "MATERIAL")
                .param("granularity", "QUARTER")
                .param("year", "2026")
                .param("period", "5"))
            .andExpect(status().isBadRequest());

        // Năm âm -> 400
        mockMvc.perform(get("/api/reports/stock")
                .param("kind", "MATERIAL")
                .param("granularity", "YEAR")
                .param("year", "-2026"))
            .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // RPT-05: BÁO CÁO CHO CẢ NVL VÀ THÀNH PHẨM (PRODUCT)
    // =========================================================================

    @Test
    void rpt05_productReport_correctValues() {
        List<StockReportItemView> report = reportService.getStockReport(
            StockReportKind.PRODUCT, ReportGranularity.MONTH, 2026, 10
        );

        assertEquals(1, report.size());
        StockReportItemView p1 = report.get(0);
        assertEquals(1L, p1.id());
        assertNull(p1.code());
        assertEquals("Bàn ăn", p1.name());
        assertEquals("cái", p1.unit());
        assertEquals(0, BigDecimal.ZERO.compareTo(p1.openingBalance()));
        assertEquals(0, new BigDecimal("10.000").compareTo(p1.inQuantity()));
        assertEquals(0, new BigDecimal("3.000").compareTo(p1.outQuantity()));
        assertEquals(0, new BigDecimal("7.000").compareTo(p1.closingBalance()));
    }
}
