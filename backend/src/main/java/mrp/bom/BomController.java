package mrp.bom;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API công thức sản phẩm BOM (P5).
 * <ul>
 *   <li>WM: tạo BOM mới, activate, deactivate.</li>
 *   <li>STAFF / WM / ADMIN: xem danh sách và chi tiết BOM.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/boms")
public class BomController {

    private final BomService service;

    public BomController(BomService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public BomViews.BomDetailView create(@Valid @RequestBody BomRequests.CreateBomRequest request) {
        return service.create(request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF', 'WAREHOUSE_MANAGER', 'ADMIN')")
    public Page<BomViews.BomView> list(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        return service.list(productId, active, page, size, sort, direction);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STAFF', 'WAREHOUSE_MANAGER', 'ADMIN')")
    public BomViews.BomDetailView detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public BomViews.BomDetailView activate(@PathVariable Long id) {
        return service.activate(id);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public BomViews.BomDetailView deactivate(@PathVariable Long id) {
        return service.deactivate(id);
    }
}
