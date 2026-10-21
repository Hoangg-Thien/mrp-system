package mrp.production.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class ProductionViews {

    public record ProductionRequestView(
        Long id,
        Long productId,
        String productName,
        String productUnit,
        Long bomId,
        Integer bomVersion,
        Integer quantity,
        Integer completedQuantity,
        Long warehouseManagerId,
        String warehouseManagerName,
        Long assignedTo,
        String assignedStaffName,
        String status,
        LocalDateTime createdAt,
        String note
    ) { }

    public record ProductionRequestDetailView(
        Long id,
        Long productId,
        String productName,
        String productUnit,
        Long bomId,
        Integer bomVersion,
        Integer quantity,
        Integer completedQuantity,
        Integer inProgressQuantity,
        Long warehouseManagerId,
        String warehouseManagerName,
        Long assignedTo,
        String assignedStaffName,
        String status,
        LocalDateTime createdAt,
        String note,
        List<ProductionExecutionView> productions,
        List<MaterialIssueRequestView> issueRequests
    ) { }

    public record RequirementView(
        Long productionRequestId,
        Long productId,
        String productName,
        Integer quantity,
        List<RequirementItemView> materials
    ) { }

    public record RequirementItemView(
        Long materialId,
        String materialCode,
        String materialName,
        String unit,
        BigDecimal bomQuantity,
        BigDecimal totalRequired,
        BigDecimal approvedQuantity,
        BigDecimal pendingQuantity,
        BigDecimal remainingAllowed,
        BigDecimal onHand,
        BigDecimal committedOther,
        BigDecimal shortage
    ) { }

    public record MaterialIssueRequestView(
        Long id,
        Long productionRequestId,
        Long requestedBy,
        String requestedByName,
        Long reviewedBy,
        String reviewedByName,
        String status,
        LocalDateTime requestedAt,
        LocalDateTime reviewedAt,
        String note,
        int itemCount
    ) { }

    public record MaterialIssueRequestDetailView(
        Long id,
        Long productionRequestId,
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
        List<MaterialIssueItemView> items
    ) { }

    public record MaterialIssueItemView(
        Long id,
        Long materialId,
        String materialCode,
        String materialName,
        String unit,
        BigDecimal quantity,
        BigDecimal currentStock
    ) { }

    public record ProductionExecutionView(
        Long id,
        Long productionRequestId,
        Integer quantity,
        Long producedBy,
        String producedByName,
        String status,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String note
    ) { }
}