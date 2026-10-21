package mrp.inventory.controller;


import mrp.inventory.dto.InventoryViews;
import mrp.inventory.service.InventoryService;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Đọc tồn kho (P3): NVL và thành phẩm. Tồn chỉ đọc, mọi thay đổi đi qua INSERT {@code stock_movements}.
 * LEFT JOIN {@code inventories} nên vật tư / sản phẩm chưa phát sinh vẫn xuất hiện với {@code quantity = 0}.
 * Sort theo whitelist; size 1..200; keyword escape {@code \%_}.
 */
@RestController
@RequestMapping("/api/inventories")
@PreAuthorize("hasAnyRole('STAFF', 'WAREHOUSE_MANAGER', 'ADMIN')")
public class InventoryController {
    private final InventoryService service;
    public InventoryController(InventoryService service) { this.service=service; }

    @GetMapping("/materials")
    public Page<InventoryViews.MaterialStockView> materials(@RequestParam(required=false) String keyword,
            @RequestParam(defaultValue="false") boolean positiveOnly,
            @RequestParam(required=false) Boolean active,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size,
            @RequestParam(defaultValue="name") String sort, @RequestParam(defaultValue="asc") String direction) {
        return service.materials(keyword, positiveOnly, active, page, size, sort, direction);
    }

    @GetMapping("/products")
    public Page<InventoryViews.ProductStockView> products(@RequestParam(required=false) String keyword,
            @RequestParam(required=false) Long categoryId,
            @RequestParam(defaultValue="false") boolean positiveOnly,
            @RequestParam(required=false) Boolean active,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size,
            @RequestParam(defaultValue="name") String sort, @RequestParam(defaultValue="asc") String direction) {
        return service.products(keyword, categoryId, positiveOnly, active, page, size, sort, direction);
    }
}