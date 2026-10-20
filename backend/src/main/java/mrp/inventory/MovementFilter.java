package mrp.inventory;

import java.time.LocalDate;

/**
 * Bộ lọc sổ kho (INV-07): loại movement, vật tư, khoảng ngày ({@code from} / {@code to} tính theo ngày,
 * inclusive cả hai đầu) và chứng từ nguồn.
 */
public record MovementFilter(MovementType type, Long materialId, Long productId, LocalDate from, LocalDate to,
                             Long purchaseReceiptId, Long materialIssueRequestId, Long productionId,
                             Long fgIssueRequestId, Long createdBy) { }
