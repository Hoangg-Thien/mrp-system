package mrp.fgissue.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class FgIssueViews {

    public record FgIssueRequestView(
        Long id,
        Long requestedBy,
        String requestedByName,
        Long reviewedBy,
        String reviewedByName,
        String status,
        LocalDateTime requestedAt,
        LocalDateTime reviewedAt,
        String note,
        int itemCount,
        int totalQuantity
    ) { }

    public record FgIssueRequestDetailView(
        Long id,
        Long requestedBy,
        String requestedByName,
        Long reviewedBy,
        String reviewedByName,
        String status,
        LocalDateTime requestedAt,
        LocalDateTime reviewedAt,
        String note,
        boolean hasStockShortage,
        String warning,
        List<FgIssueItemView> items
    ) { }

    public record FgIssueItemView(
        Long id,
        Long productId,
        String productName,
        String productUnit,
        Integer quantity,
        BigDecimal currentStock
    ) { }
}