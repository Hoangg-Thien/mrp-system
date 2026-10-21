package mrp.fgissue.controller;


import mrp.fgissue.dto.FgIssueRequests;
import mrp.fgissue.dto.FgIssueViews;
import mrp.fgissue.entity.FgIssueRequestStatus;
import mrp.fgissue.service.FgIssueService;
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
@RequestMapping("/api/fg-issue-requests")
public class FgIssueController {

    private final FgIssueService service;
    private final CurrentUserResolver currentUser;

    public FgIssueController(FgIssueService service, CurrentUserResolver currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping
    @PreAuthorize("hasRole('STAFF')")
    public FgIssueViews.FgIssueRequestDetailView create(
            @Valid @RequestBody FgIssueRequests.CreateFgIssueRequest request,
            Authentication auth) {
        return service.create(request, currentUser.resolve(auth));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF', 'WAREHOUSE_MANAGER', 'ADMIN')")
    public Page<FgIssueViews.FgIssueRequestView> list(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) FgIssueRequestStatus status,
            @RequestParam(required = false) Long requestedBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            Authentication auth) {
        CurrentUser actor = currentUser.resolve(auth);
        boolean isStaff = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_STAFF"));
        return service.list(productId, status, requestedBy, page, size, sort, direction, actor, isStaff);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STAFF', 'WAREHOUSE_MANAGER', 'ADMIN')")
    public FgIssueViews.FgIssueRequestDetailView detail(
            @PathVariable Long id,
            Authentication auth) {
        return service.detail(id, currentUser.resolve(auth));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public FgIssueViews.FgIssueRequestDetailView approve(
            @PathVariable Long id,
            Authentication auth) {
        return service.approve(id, currentUser.resolve(auth));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public FgIssueViews.FgIssueRequestDetailView reject(
            @PathVariable Long id,
            @Valid @RequestBody FgIssueRequests.RejectRequest request,
            Authentication auth) {
        return service.reject(id, request, currentUser.resolve(auth));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('STAFF')")
    public FgIssueViews.FgIssueRequestDetailView cancel(
            @PathVariable Long id,
            Authentication auth) {
        return service.cancel(id, currentUser.resolve(auth));
    }
}