package mrp.catalog.controller;


import mrp.catalog.dto.CatalogRequests;
import mrp.catalog.entity.Material;
import mrp.catalog.service.CatalogService;
import jakarta.validation.Valid;
import mrp.catalog.dto.CatalogRequests.MaterialRequest;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/materials")
@PreAuthorize("hasAnyRole('STAFF', 'WAREHOUSE_MANAGER', 'ADMIN')")
public class MaterialController {
    private final CatalogService service;
    public MaterialController(CatalogService service) { this.service=service; }
    @GetMapping public Page<Material> list(@RequestParam(required=false) String keyword,
            @RequestParam(required=false) Boolean active,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size,
            @RequestParam(defaultValue="name") String sort, @RequestParam(defaultValue="asc") String direction) {
        return service.materials(keyword, active, page, size, sort, direction);
    }
    @GetMapping("/{id}") public Material detail(@PathVariable Long id) { return service.material(id); }
    @PostMapping @PreAuthorize("hasRole('ADMIN')")
    public Material create(@Valid @RequestBody MaterialRequest r) { return service.createMaterial(r); }
    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')")
    public Material update(@PathVariable Long id, @Valid @RequestBody MaterialRequest r) { return service.updateMaterial(id, r); }
    @DeleteMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.deleteMaterial(id); }
    @PostMapping("/{id}/activate") @PreAuthorize("hasRole('ADMIN')")
    public Material activate(@PathVariable Long id) { return service.materialActive(id, true); }
    @PostMapping("/{id}/deactivate") @PreAuthorize("hasRole('ADMIN')")
    public Material deactivate(@PathVariable Long id) { return service.materialActive(id, false); }
}