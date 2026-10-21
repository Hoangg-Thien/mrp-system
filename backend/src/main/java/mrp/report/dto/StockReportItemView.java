package mrp.report.dto;

import java.math.BigDecimal;

public record StockReportItemView(
    Long id,
    String code,
    String name,
    String unit,
    BigDecimal openingBalance,
    BigDecimal inQuantity,
    BigDecimal outQuantity,
    BigDecimal adjustInQuantity,
    BigDecimal adjustOutQuantity,
    BigDecimal closingBalance
) {
    public Long getItemId() { return id; }
    public String getItemCode() { return code; }
    public String getItemName() { return name; }
}