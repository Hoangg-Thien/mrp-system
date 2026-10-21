package mrp.production.controller;


import mrp.production.dto.ProductionRequests;
import mrp.production.dto.ProductionViews;
import mrp.production.entity.ProductionRequestStatus;
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
@RequestMapping("/api/production-requests")
public class ProductionRequestController {

    private final ProductionService service;
    private final CurrentUserResolver currentUser;

    public ProductionRequestController(ProductionService service, CurrentUserResolver currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public ProductionViews.ProductionRequestDetailView create(
            @Valid @RequestBody ProductionRequests.CreateProductionRequest request,
            Authentication auth) {
        return service.createProductionRequest(request, currentUser.resolve(auth));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF', 'WAREHOUSE_MANAGER', 'ADMIN')")
    public Page<ProductionViews.ProductionRequestView> list(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) ProductionRequestStatus status,
            @RequestParam(required = false) Long assignedTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            Authentication auth) {
        CurrentUser actor = currentUser.resolve(auth);
        boolean isStaff = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_STAFF"));
        return service.listProductionRequests(productId, status, assignedTo, page, size, sort, direction, actor, isStaff);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STAFF', 'WAREHOUSE_MANAGER', 'ADMIN')")
    public ProductionViews.ProductionRequestDetailView detail(
            @PathVariable Long id,
            Authentication auth) {
        return service.detailProductionRequest(id, currentUser.resolve(auth));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public ProductionViews.ProductionRequestDetailView cancel(
            @PathVariable Long id,
            Authentication auth) {
        return service.cancelProductionRequest(id, currentUser.resolve(auth));
    }

    @GetMapping("/{id}/requirements")
    @PreAuthorize("hasAnyRole('STAFF', 'WAREHOUSE_MANAGER')")
    public ProductionViews.RequirementView getRequirements(
            @PathVariable Long id,
            Authentication auth) {
        return service.getRequirements(id, currentUser.resolve(auth));
    }
}