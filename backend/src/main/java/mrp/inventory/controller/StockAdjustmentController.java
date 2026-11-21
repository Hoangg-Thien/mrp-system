package mrp.inventory.controller;


import mrp.inventory.dto.InventoryRequests;
import mrp.inventory.dto.InventoryViews;
import mrp.inventory.service.InventoryService;
import mrp.inventory.service.StockLedger;
import mrp.security.CurrentUser;
import mrp.security.CurrentUserResolver;
import jakarta.validation.Valid;
import mrp.inventory.dto.InventoryRequests.StockAdjustmentRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Điều chỉnh tồn (P3, chỉ WM): ghi một dòng {@code ADJUST_IN} / {@code ADJUST_OUT} qua {@link StockLedger}.
 * Tồn do trigger tính, không bao giờ UPDATE {@code inventories} trực tiếp.
 * Đúng một trong {@code materialId}/{@code productId}; số lượng &gt; 0; thành phẩm phải nguyên [DB ck];
 * {@code note} bắt buộc [DB ck_sm_adjust_note]; {@code ADJUST_OUT} vượt tồn → 409 [DB CHECK 23514] và không để lại movement.
 */
@RestController
@RequestMapping("/api/stock-adjustments")
@PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
public class StockAdjustmentController {
    private final InventoryService service;
    private final CurrentUserResolver currentUser;
    public StockAdjustmentController(InventoryService service, CurrentUserResolver currentUser) {
        this.service=service; this.currentUser=currentUser;
    }

    @PostMapping
    public InventoryViews.StockAdjustmentView adjust(@Valid @RequestBody StockAdjustmentRequest request,
                                                     Authentication auth) {
        CurrentUser actor = currentUser.resolve(auth);
        return service.adjust(request, actor);
    }
}