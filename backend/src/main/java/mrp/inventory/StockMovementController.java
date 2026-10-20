package mrp.inventory;

import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Sổ kho (P3, chỉ đọc): {@code stock_movements} append-only (trigger {@code trg_sm_ro} chặn UPDATE/DELETE).
 * Lọc theo loại movement, vật tư, khoảng ngày (inclusive cả hai đầu, ISO {@code yyyy-MM-dd}) và chứng từ nguồn.
 * Enum {@link MovementType} / {@link LocalDate} sai → {@code MethodArgumentTypeMismatchException} → 400.
 */
@RestController
@RequestMapping("/api/stock-movements")
@PreAuthorize("hasAnyRole('STAFF', 'WAREHOUSE_MANAGER', 'ADMIN')")
public class StockMovementController {
    private final InventoryService service;
    public StockMovementController(InventoryService service) { this.service=service; }

    @GetMapping
    public Page<InventoryViews.StockMovementView> list(@RequestParam(required=false) MovementType type,
            @RequestParam(required=false) Long materialId, @RequestParam(required=false) Long productId,
            @RequestParam(required=false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required=false) Long purchaseReceiptId,
            @RequestParam(required=false) Long materialIssueRequestId,
            @RequestParam(required=false) Long productionId,
            @RequestParam(required=false) Long fgIssueRequestId,
            @RequestParam(required=false) Long createdBy,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size,
            @RequestParam(defaultValue="createdAt") String sort, @RequestParam(defaultValue="desc") String direction) {
        MovementFilter filter = new MovementFilter(type, materialId, productId, from, to,
                purchaseReceiptId, materialIssueRequestId, productionId, fgIssueRequestId, createdBy);
        return service.movements(filter, page, size, sort, direction);
    }
}
