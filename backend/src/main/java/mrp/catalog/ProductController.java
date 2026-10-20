package mrp.catalog;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import mrp.catalog.CatalogRequests.ProductRequest;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@PreAuthorize("hasAnyRole('STAFF', 'WAREHOUSE_MANAGER', 'ADMIN')")
public class ProductController {
    private final CatalogService service;
    public ProductController(CatalogService service) { this.service=service; }
    @GetMapping public Page<Product> list(@RequestParam(required=false) String keyword,
            @RequestParam(required=false) Long categoryId, @RequestParam(required=false) BigDecimal minPrice,
            @RequestParam(required=false) BigDecimal maxPrice, @RequestParam(required=false) Boolean active,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size,
            @RequestParam(defaultValue="name") String sort, @RequestParam(defaultValue="asc") String direction,
            Authentication authentication) {
        if (active != null && authentication.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new AccessDeniedException("Chỉ ADMIN được lọc active");
        }
        return service.products(keyword, categoryId, minPrice, maxPrice, active == null ? true : active, page, size, sort, direction);
    }
    @GetMapping("/{id}") public Product detail(@PathVariable Long id) { return service.product(id); }
    @PostMapping @PreAuthorize("hasRole('ADMIN')")
    public Product create(@Valid @RequestBody ProductRequest r) { return service.createProduct(r); }
    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')")
    public Product update(@PathVariable Long id, @Valid @RequestBody ProductRequest r) { return service.updateProduct(id, r); }
    @DeleteMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.deleteProduct(id); }
    @PostMapping("/{id}/activate") @PreAuthorize("hasRole('ADMIN')")
    public Product activate(@PathVariable Long id) { return service.productActive(id, true); }
    @PostMapping("/{id}/deactivate") @PreAuthorize("hasRole('ADMIN')")
    public Product deactivate(@PathVariable Long id) { return service.productActive(id, false); }
}