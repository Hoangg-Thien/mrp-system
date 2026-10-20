package mrp.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import mrp.common.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class StockReportServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    private StockReportService service;

    @BeforeEach
    void setUp() {
        service = new StockReportService(jdbc);
    }

    // =========================================================================
    // RPT-04: KIỂM TRA VALIDATION THAM SỐ (400 BAD REQUEST)
    // =========================================================================

    @Test
    void rpt04_nullKind_throwsBadRequestException() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
            service.getStockReport(null, "MONTH", 2026, 10)
        );
        assertTrue(ex.getMessage().contains("kind không được để trống"));
    }

    @Test
    void rpt04_invalidKind_throwsBadRequestException() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
            service.getStockReport("UNKNOWN", "MONTH", 2026, 10)
        );
        assertTrue(ex.getMessage().contains("kind phải là MATERIAL hoặc PRODUCT"));
    }

    @Test
    void rpt04_nullGranularity_throwsBadRequestException() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
            service.getStockReport("MATERIAL", null, 2026, 10)
        );
        assertTrue(ex.getMessage().contains("granularity không được để trống"));
    }

    @Test
    void rpt04_invalidGranularity_throwsBadRequestException() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
            service.getStockReport("MATERIAL", "WEEK", 2026, 10)
        );
        assertTrue(ex.getMessage().contains("granularity phải là MONTH, QUARTER hoặc YEAR"));
    }

    @Test
    void rpt04_nullYear_throwsBadRequestException() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
            service.getStockReport("MATERIAL", "MONTH", null, 10)
        );
        assertTrue(ex.getMessage().contains("Năm không được để trống"));
    }

    @Test
    void rpt04_negativeYear_throwsBadRequestException() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
            service.getStockReport("MATERIAL", "MONTH", -2026, 10)
        );
        assertTrue(ex.getMessage().contains("Năm không hợp lệ"));
    }

    @Test
    void rpt04_nullPeriodForMonth_throwsBadRequestException() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
            service.getStockReport("MATERIAL", "MONTH", 2026, null)
        );
        assertTrue(ex.getMessage().contains("period không được để trống khi granularity là MONTH"));
    }

    @Test
    void rpt04_monthPeriod13_throwsBadRequestException() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
            service.getStockReport("MATERIAL", "MONTH", 2026, 13)
        );
        assertTrue(ex.getMessage().contains("period cho tháng phải từ 1 đến 12"));
    }

    @Test
    void rpt04_monthPeriod0_throwsBadRequestException() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
            service.getStockReport("MATERIAL", "MONTH", 2026, 0)
        );
        assertTrue(ex.getMessage().contains("period cho tháng phải từ 1 đến 12"));
    }

    @Test
    void rpt04_nullPeriodForQuarter_throwsBadRequestException() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
            service.getStockReport("MATERIAL", "QUARTER", 2026, null)
        );
        assertTrue(ex.getMessage().contains("period không được để trống khi granularity là QUARTER"));
    }

    @Test
    void rpt04_quarterPeriod5_throwsBadRequestException() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
            service.getStockReport("MATERIAL", "QUARTER", 2026, 5)
        );
        assertTrue(ex.getMessage().contains("period cho quý phải từ 1 đến 4"));
    }

    @Test
    void rpt04_quarterPeriod0_throwsBadRequestException() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
            service.getStockReport("MATERIAL", "QUARTER", 2026, 0)
        );
        assertTrue(ex.getMessage().contains("period cho quý phải từ 1 đến 4"));
    }

    @Test
    void rpt04_yearPeriodNegative_throwsBadRequestException() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
            service.getStockReport("MATERIAL", "YEAR", 2026, -1)
        );
        assertTrue(ex.getMessage().contains("period không hợp lệ"));
    }

    // =========================================================================
    // BIÊN THỜI GIAN (BOUNDARIES) CHO MONTH / QUARTER / YEAR
    // =========================================================================

    @Test
    void rpt01_monthPeriodBoundaries_correctTimestampsPassedToJdbc() {
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any(), any(), any(), any()))
            .thenReturn(List.of());

        service.getStockReport("MATERIAL", "MONTH", 2026, 10);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).query(anyString(), any(RowMapper.class),
            captor.capture(), captor.capture(), captor.capture(),
            captor.capture(), captor.capture(), captor.capture());

        List<Object> args = captor.getAllValues();
        Timestamp endTs = (Timestamp) args.get(0);
        Timestamp startTs = (Timestamp) args.get(1);

        assertEquals(Timestamp.valueOf(LocalDateTime.of(2026, 11, 1, 0, 0, 0)), endTs);
        assertEquals(Timestamp.valueOf(LocalDateTime.of(2026, 10, 1, 0, 0, 0)), startTs);
    }

    @Test
    void rpt01_quarterPeriodBoundaries_correctTimestampsPassedToJdbc() {
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any(), any(), any(), any()))
            .thenReturn(List.of());

        // Quý 1: 01/01 -> 01/04
        service.getStockReport("MATERIAL", "QUARTER", 2026, 1);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).query(anyString(), any(RowMapper.class),
            captor.capture(), captor.capture(), captor.capture(),
            captor.capture(), captor.capture(), captor.capture());

        assertEquals(Timestamp.valueOf(LocalDateTime.of(2026, 4, 1, 0, 0, 0)), captor.getAllValues().get(0));
        assertEquals(Timestamp.valueOf(LocalDateTime.of(2026, 1, 1, 0, 0, 0)), captor.getAllValues().get(1));
    }

    @Test
    void rpt01_yearPeriodBoundaries_correctTimestampsPassedToJdbc() {
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any(), any(), any(), any()))
            .thenReturn(List.of());

        // Năm 2026: 01/01/2026 -> 01/01/2027
        service.getStockReport("MATERIAL", "YEAR", 2026, null);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).query(anyString(), any(RowMapper.class),
            captor.capture(), captor.capture(), captor.capture(),
            captor.capture(), captor.capture(), captor.capture());

        assertEquals(Timestamp.valueOf(LocalDateTime.of(2027, 1, 1, 0, 0, 0)), captor.getAllValues().get(0));
        assertEquals(Timestamp.valueOf(LocalDateTime.of(2026, 1, 1, 0, 0, 0)), captor.getAllValues().get(1));
    }

    // =========================================================================
    // RPT-05: BÁO CÁO CHO PRODUCT (THÀNH PHẨM)
    // =========================================================================

    @Test
    void rpt05_productReportQuery_executed() {
        StockReportItemView productItem = new StockReportItemView(
            1L, null, "Bàn trà", "cái",
            BigDecimal.valueOf(10), BigDecimal.valueOf(5), BigDecimal.valueOf(3),
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(12)
        );

        when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any(), any(), any(), any()))
            .thenReturn(List.of(productItem));

        List<StockReportItemView> result = service.getStockReport("PRODUCT", "MONTH", 2026, 10);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).id());
        assertEquals("Bàn trà", result.get(0).name());
        assertNull(result.get(0).code());
        assertEquals(BigDecimal.valueOf(12), result.get(0).closingBalance());
    }

    // =========================================================================
    // TÍNH TOÁN CLOSING BALANCE
    // =========================================================================

    @Test
    void rpt01_stockReportItemView_convenienceMethods() {
        StockReportItemView item = new StockReportItemView(
            100L, "M01", "Gỗ", "m3",
            new BigDecimal("10.000"), new BigDecimal("5.000"), new BigDecimal("2.000"),
            new BigDecimal("1.000"), new BigDecimal("0.500"), new BigDecimal("13.500")
        );

        assertEquals(100L, item.getItemId());
        assertEquals("M01", item.getItemCode());
        assertEquals("Gỗ", item.getItemName());
        assertEquals(new BigDecimal("13.500"), item.closingBalance());
    }
}
