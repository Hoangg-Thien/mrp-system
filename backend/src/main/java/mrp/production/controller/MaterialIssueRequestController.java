package mrp.production.controller;


import mrp.production.dto.ProductionRequests;
import mrp.production.dto.ProductionViews;
import mrp.production.entity.MaterialIssueRequestStatus;
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
@RequestMapping("/api/material-issue-requests")
public class MaterialIssueRequestController {

    private final ProductionService service;
    private final CurrentUserResolver currentUser;

    public MaterialIssueRequestController(ProductionService service, CurrentUserResolver currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping
    @PreAuthorize("hasRole('STAFF')")
    public ProductionViews.MaterialIssueRequestDetailView create(
            @Valid @RequestBody ProductionRequests.CreateMaterialIssueRequest request,
            Authentication auth) {
        return service.createMaterialIssueRequest(request, currentUser.resolve(auth));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF', 'WAREHOUSE_MANAGER', 'ADMIN')")
    public Page<ProductionViews.MaterialIssueRequestView> list(
            @RequestParam(required = false) Long productionRequestId,
            @RequestParam(required = false) MaterialIssueRequestStatus status,
            @RequestParam(required = false) Long requestedBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            Authentication auth) {
        CurrentUser actor = currentUser.resolve(auth);
        boolean isStaff = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_STAFF"));
        return service.listMaterialIssueRequests(productionRequestId, status, requestedBy, page, size, sort, direction, actor, isStaff);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STAFF', 'WAREHOUSE_MANAGER', 'ADMIN')")
    public ProductionViews.MaterialIssueRequestDetailView detail(
            @PathVariable Long id,
            Authentication auth) {
        return service.detailMaterialIssueRequest(id, currentUser.resolve(auth));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public ProductionViews.MaterialIssueRequestDetailView approve(
            @PathVariable Long id,
            Authentication auth) {
        return service.approveMaterialIssueRequest(id, currentUser.resolve(auth));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public ProductionViews.MaterialIssueRequestDetailView reject(
            @PathVariable Long id,
            @Valid @RequestBody ProductionRequests.RejectRequest request,
            Authentication auth) {
        return service.rejectMaterialIssueRequest(id, request, currentUser.resolve(auth));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('STAFF')")
    public ProductionViews.MaterialIssueRequestDetailView cancel(
            @PathVariable Long id,
            Authentication auth) {
        return service.cancelMaterialIssueRequest(id, currentUser.resolve(auth));
    }
}