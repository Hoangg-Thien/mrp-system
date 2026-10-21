package mrp.production.controller;


import mrp.production.dto.ProductionRequests;
import mrp.production.dto.ProductionViews;
import mrp.production.entity.ProductionStatus;
import mrp.production.service.ProductionService;
import mrp.security.CurrentUser;
import mrp.security.CurrentUserResolver;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/productions")
public class ProductionController {

    private final ProductionService service;
    private final CurrentUserResolver currentUser;

    public ProductionController(ProductionService service, CurrentUserResolver currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping
    @PreAuthorize("hasRole('STAFF')")
    public ProductionViews.ProductionExecutionView create(
            @Valid @RequestBody ProductionRequests.CreateProductionExecutionRequest request,
            Authentication auth) {
        return service.createProduction(request, currentUser.resolve(auth));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF', 'WAREHOUSE_MANAGER', 'ADMIN')")
    public Page<ProductionViews.ProductionExecutionView> list(
            @RequestParam(required = false) Long productionRequestId,
            @RequestParam(required = false) ProductionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            Authentication auth) {
        CurrentUser actor = currentUser.resolve(auth);
        boolean isStaff = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_STAFF"));
        return service.listProductions(productionRequestId, status, page, size, sort, direction, actor, isStaff);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STAFF', 'WAREHOUSE_MANAGER', 'ADMIN')")
    public ProductionViews.ProductionExecutionView detail(
            @PathVariable Long id,
            Authentication auth) {
        return service.detailProduction(id, currentUser.resolve(auth));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('STAFF')")
    public ProductionViews.ProductionExecutionView complete(
            @PathVariable Long id,
            Authentication auth) {
        return service.completeProduction(id, currentUser.resolve(auth));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('STAFF')")
    public ProductionViews.ProductionExecutionView cancel(
            @PathVariable Long id,
            Authentication auth) {
        return service.cancelProduction(id, currentUser.resolve(auth));
    }
}