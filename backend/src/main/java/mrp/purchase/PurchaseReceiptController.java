package mrp.purchase;

import jakarta.validation.Valid;
import java.time.LocalDate;
import mrp.security.CurrentUserResolver;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST phiếu nhập kho NVL (P4). Phân quyền:
 * <ul>
 *   <li>WM: tạo / sửa dòng (PENDING) / complete / cancel.</li>
 *   <li>STAFF / WM / ADMIN: xem danh sách + chi tiết.</li>
 * </ul>
 * {@code complete} cần {@link Authentication} để lấy {@code actor.userId()} cho {@code created_by} của movement
 * [users.id, khác {@code warehouse_manager_id} = profile id].
 */
@RestController
@RequestMapping("/api/purchase-receipts")
public class PurchaseReceiptController {
    private final PurchaseService service;
    private final CurrentUserResolver currentUser;

    public PurchaseReceiptController(PurchaseService service, CurrentUserResolver currentUser) {
        this.service = service; this.currentUser = currentUser;
    }

    @PostMapping
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public PurchaseViews.PurchaseReceiptDetailView create(@Valid @RequestBody PurchaseRequests.PurchaseReceiptRequest request,
                                                          Authentication auth) {
        return service.create(request, currentUser.resolve(auth));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF', 'WAREHOUSE_MANAGER', 'ADMIN')")
    public Page<PurchaseViews.PurchaseReceiptView> list(@RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) PurchaseReceiptStatus status,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Long warehouseManagerId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sort, @RequestParam(defaultValue = "desc") String direction) {
        return service.list(supplierId, status, from, to, warehouseManagerId, page, size, sort, direction);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STAFF', 'WAREHOUSE_MANAGER', 'ADMIN')")
    public PurchaseViews.PurchaseReceiptDetailView detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public PurchaseViews.PurchaseReceiptDetailView replaceItems(@PathVariable Long id,
            @Valid @RequestBody PurchaseRequests.PurchaseReceiptRequest request) {
        return service.replaceItems(id, request);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public PurchaseViews.PurchaseReceiptDetailView complete(@PathVariable Long id, Authentication auth) {
        return service.complete(id, currentUser.resolve(auth));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public PurchaseViews.PurchaseReceiptDetailView cancel(@PathVariable Long id) {
        return service.cancel(id);
    }
}
