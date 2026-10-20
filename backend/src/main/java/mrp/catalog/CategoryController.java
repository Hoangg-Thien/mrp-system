package mrp.catalog;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import mrp.catalog.CatalogRequests.CategoryRequest;

@RestController
@RequestMapping("/api/categories")
@PreAuthorize("hasAnyRole('STAFF', 'WAREHOUSE_MANAGER', 'ADMIN')")
public class CategoryController {
    private final CatalogService service;
    public CategoryController(CatalogService service) { this.service=service; }
    @GetMapping public Page<Category> list(@RequestParam(required=false) String keyword,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size,
            @RequestParam(defaultValue="name") String sort, @RequestParam(defaultValue="asc") String direction) {
        return service.categories(keyword, page, size, sort, direction);
    }
    @GetMapping("/{id}") public Category detail(@PathVariable Long id) { return service.category(id); }
    @PostMapping @PreAuthorize("hasRole('ADMIN')")
    public Category create(@Valid @RequestBody CategoryRequest r) { return service.createCategory(r); }
    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')")
    public Category update(@PathVariable Long id, @Valid @RequestBody CategoryRequest r) { return service.updateCategory(id, r); }
    @DeleteMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.deleteCategory(id); }
}