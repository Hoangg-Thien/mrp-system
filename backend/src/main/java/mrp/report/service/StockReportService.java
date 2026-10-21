package mrp.report.service;


import mrp.common.exception.BadRequestException;
import mrp.report.dto.ReportGranularity;
import mrp.report.dto.StockReportItemView;
import mrp.report.dto.StockReportKind;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StockReportService {

    private final JdbcTemplate jdbc;

    public StockReportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<StockReportItemView> getStockReport(
            String kindStr, String granularityStr, Integer year, Integer period) {

        if (kindStr == null || kindStr.isBlank()) {
            throw new BadRequestException("kind không được để trống (phải là MATERIAL hoặc PRODUCT)");
        }
        StockReportKind kind;
        try {
            kind = StockReportKind.valueOf(kindStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("kind phải là MATERIAL hoặc PRODUCT (nhận được: " + kindStr + ")");
        }

        if (granularityStr == null || granularityStr.isBlank()) {
            throw new BadRequestException("granularity không được để trống (phải là MONTH, QUARTER hoặc YEAR)");
        }
        ReportGranularity granularity;
        try {
            granularity = ReportGranularity.valueOf(granularityStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("granularity phải là MONTH, QUARTER hoặc YEAR (nhận được: " + granularityStr + ")");
        }

        if (year == null) {
            throw new BadRequestException("Năm không được để trống");
        }
        if (year <= 0 || year > 9999) {
            throw new BadRequestException("Năm không hợp lệ: " + year);
        }

        return getStockReport(kind, granularity, year, period);
    }

    public List<StockReportItemView> getStockReport(
            StockReportKind kind, ReportGranularity granularity, int year, Integer period) {

        if (year <= 0 || year > 9999) {
            throw new BadRequestException("Năm không hợp lệ: " + year);
        }

        LocalDateTime startInclusive;
        LocalDateTime endExclusive;

        switch (granularity) {
            case MONTH -> {
                if (period == null) {
                    throw new BadRequestException("period không được để trống khi granularity là MONTH");
                }
                if (period < 1 || period > 12) {
                    throw new BadRequestException("period cho tháng phải từ 1 đến 12 (nhận được: " + period + ")");
                }
                startInclusive = LocalDateTime.of(year, period, 1, 0, 0, 0);
                endExclusive = startInclusive.plusMonths(1);
            }
            case QUARTER -> {
                if (period == null) {
                    throw new BadRequestException("period không được để trống khi granularity là QUARTER");
                }
                if (period < 1 || period > 4) {
                    throw new BadRequestException("period cho quý phải từ 1 đến 4 (nhận được: " + period + ")");
                }
                int startMonth = (period - 1) * 3 + 1;
                startInclusive = LocalDateTime.of(year, startMonth, 1, 0, 0, 0);
                endExclusive = startInclusive.plusMonths(3);
            }
            case YEAR -> {
                if (period != null && period <= 0) {
                    throw new BadRequestException("period không hợp lệ: " + period);
                }
                startInclusive = LocalDateTime.of(year, 1, 1, 0, 0, 0);
                endExclusive = LocalDateTime.of(year + 1, 1, 1, 0, 0, 0);
            }
            default -> throw new BadRequestException("granularity không được hỗ trợ: " + granularity);
        }

        Timestamp endTs = Timestamp.valueOf(endExclusive);
        Timestamp startTs = Timestamp.valueOf(startInclusive);

        if (kind == StockReportKind.MATERIAL) {
            return queryMaterialReport(startTs, endTs);
        } else {
            return queryProductReport(startTs, endTs);
        }
    }

    private List<StockReportItemView> queryMaterialReport(Timestamp startTs, Timestamp endTs) {
        String sql = """
            WITH movements AS (
                SELECT 
                    material_id,
                    movement_type,
                    quantity,
                    created_at
                FROM stock_movements
                WHERE material_id IS NOT NULL AND created_at < ?
            )
            SELECT 
                m.id AS id,
                m.code AS code,
                m.name AS name,
                m.unit AS unit,
                COALESCE(SUM(CASE 
                    WHEN sm.created_at < ? THEN
                        CASE 
                            WHEN sm.movement_type IN ('IN', 'ADJUST_IN') THEN sm.quantity
                            WHEN sm.movement_type IN ('OUT', 'ADJUST_OUT') THEN -sm.quantity
                            ELSE 0
                        END
                    ELSE 0
                END), 0) AS opening_balance,
                COALESCE(SUM(CASE WHEN sm.created_at >= ? AND sm.movement_type = 'IN' THEN sm.quantity ELSE 0 END), 0) AS in_quantity,
                COALESCE(SUM(CASE WHEN sm.created_at >= ? AND sm.movement_type = 'OUT' THEN sm.quantity ELSE 0 END), 0) AS out_quantity,
                COALESCE(SUM(CASE WHEN sm.created_at >= ? AND sm.movement_type = 'ADJUST_IN' THEN sm.quantity ELSE 0 END), 0) AS adjust_in_quantity,
                COALESCE(SUM(CASE WHEN sm.created_at >= ? AND sm.movement_type = 'ADJUST_OUT' THEN sm.quantity ELSE 0 END), 0) AS adjust_out_quantity
            FROM materials m
            LEFT JOIN movements sm ON sm.material_id = m.id
            GROUP BY m.id, m.code, m.name, m.unit
            ORDER BY m.id ASC
            """;

        return jdbc.query(sql, (rs, rowNum) -> {
            Long id = rs.getLong("id");
            String code = rs.getString("code");
            String name = rs.getString("name");
            String unit = rs.getString("unit");

            BigDecimal opening = rs.getBigDecimal("opening_balance");
            if (opening == null) opening = BigDecimal.ZERO;

            BigDecimal inQty = rs.getBigDecimal("in_quantity");
            if (inQty == null) inQty = BigDecimal.ZERO;

            BigDecimal outQty = rs.getBigDecimal("out_quantity");
            if (outQty == null) outQty = BigDecimal.ZERO;

            BigDecimal adjInQty = rs.getBigDecimal("adjust_in_quantity");
            if (adjInQty == null) adjInQty = BigDecimal.ZERO;

            BigDecimal adjOutQty = rs.getBigDecimal("adjust_out_quantity");
            if (adjOutQty == null) adjOutQty = BigDecimal.ZERO;

            BigDecimal closing = opening.add(inQty).add(adjInQty).subtract(outQty).subtract(adjOutQty);

            return new StockReportItemView(
                id, code, name, unit,
                opening, inQty, outQty, adjInQty, adjOutQty, closing
            );
        }, endTs, startTs, startTs, startTs, startTs, startTs);
    }

    private List<StockReportItemView> queryProductReport(Timestamp startTs, Timestamp endTs) {
        String sql = """
            WITH movements AS (
                SELECT 
                    product_id,
                    movement_type,
                    quantity,
                    created_at
                FROM stock_movements
                WHERE product_id IS NOT NULL AND created_at < ?
            )
            SELECT 
                p.id AS id,
                NULL AS code,
                p.name AS name,
                p.unit AS unit,
                COALESCE(SUM(CASE 
                    WHEN sm.created_at < ? THEN
                        CASE 
                            WHEN sm.movement_type IN ('IN', 'ADJUST_IN') THEN sm.quantity
                            WHEN sm.movement_type IN ('OUT', 'ADJUST_OUT') THEN -sm.quantity
                            ELSE 0
                        END
                    ELSE 0
                END), 0) AS opening_balance,
                COALESCE(SUM(CASE WHEN sm.created_at >= ? AND sm.movement_type = 'IN' THEN sm.quantity ELSE 0 END), 0) AS in_quantity,
                COALESCE(SUM(CASE WHEN sm.created_at >= ? AND sm.movement_type = 'OUT' THEN sm.quantity ELSE 0 END), 0) AS out_quantity,
                COALESCE(SUM(CASE WHEN sm.created_at >= ? AND sm.movement_type = 'ADJUST_IN' THEN sm.quantity ELSE 0 END), 0) AS adjust_in_quantity,
                COALESCE(SUM(CASE WHEN sm.created_at >= ? AND sm.movement_type = 'ADJUST_OUT' THEN sm.quantity ELSE 0 END), 0) AS adjust_out_quantity
            FROM products p
            LEFT JOIN movements sm ON sm.product_id = p.id
            GROUP BY p.id, p.name, p.unit
            ORDER BY p.id ASC
            """;

        return jdbc.query(sql, (rs, rowNum) -> {
            Long id = rs.getLong("id");
            String code = rs.getString("code");
            String name = rs.getString("name");
            String unit = rs.getString("unit");

            BigDecimal opening = rs.getBigDecimal("opening_balance");
            if (opening == null) opening = BigDecimal.ZERO;

            BigDecimal inQty = rs.getBigDecimal("in_quantity");
            if (inQty == null) inQty = BigDecimal.ZERO;

            BigDecimal outQty = rs.getBigDecimal("out_quantity");
            if (outQty == null) outQty = BigDecimal.ZERO;

            BigDecimal adjInQty = rs.getBigDecimal("adjust_in_quantity");
            if (adjInQty == null) adjInQty = BigDecimal.ZERO;

            BigDecimal adjOutQty = rs.getBigDecimal("adjust_out_quantity");
            if (adjOutQty == null) adjOutQty = BigDecimal.ZERO;

            BigDecimal closing = opening.add(inQty).add(adjInQty).subtract(outQty).subtract(adjOutQty);

            return new StockReportItemView(
                id, code, name, unit,
                opening, inQty, outQty, adjInQty, adjOutQty, closing
            );
        }, endTs, startTs, startTs, startTs, startTs, startTs);
    }
}