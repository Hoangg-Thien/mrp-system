package mrp.catalog;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import mrp.catalog.CatalogRequests.SupplierRequest;

@RestController
@RequestMapping("/api/suppliers")
@PreAuthorize("hasAnyRole('STAFF', 'WAREHOUSE_MANAGER', 'ADMIN')")
public class SupplierController {
    private final CatalogService service;
    public SupplierController(CatalogService service) { this.service=service; }
    @GetMapping public Page<Supplier> list(@RequestParam(required=false) String keyword,
            @RequestParam(required=false) String name, @RequestParam(required=false) String phone,
            @RequestParam(required=false) String email,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size,
            @RequestParam(defaultValue="name") String sort, @RequestParam(defaultValue="asc") String direction) {
        return service.suppliers(keyword, name, phone, email, page, size, sort, direction);
    }
    @GetMapping("/{id}") public Supplier detail(@PathVariable Long id) { return service.supplier(id); }
    @PostMapping @PreAuthorize("hasRole('ADMIN')")
    public Supplier create(@Valid @RequestBody SupplierRequest r) { return service.createSupplier(r); }
    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')")
    public Supplier update(@PathVariable Long id, @Valid @RequestBody SupplierRequest r) { return service.updateSupplier(id, r); }
    @DeleteMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.deleteSupplier(id); }
}