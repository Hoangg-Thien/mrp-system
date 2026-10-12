package mrp.admin;

import jakarta.validation.Valid;
import mrp.auth.UserView;
import mrp.user.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    private final AdminUserService service;
    public AdminUserController(AdminUserService service) { this.service=service; }

    @GetMapping public Page<UserView> list(@RequestParam(required=false) UserRole role,
                                           @RequestParam(required=false) Boolean active,
                                           @RequestParam(required=false) String keyword,
                                           @RequestParam(defaultValue="0") int page,
                                           @RequestParam(defaultValue="20") int size,
                                           @RequestParam(defaultValue="createdAt") String sort,
                                           @RequestParam(defaultValue="desc") String direction) {
        return service.list(role, active, keyword, page, size, sort, direction);
    }
    @PostMapping public UserView create(@Valid @RequestBody CreateUserRequest request) { return service.create(request); }
    @GetMapping("/{id}") public UserView detail(@PathVariable Long id) { return service.detail(id); }
    @PostMapping("/{id}/lock") public UserView lock(@PathVariable Long id, Authentication auth) { return service.lock(id, auth); }
    @PostMapping("/{id}/unlock") public UserView unlock(@PathVariable Long id) { return service.unlock(id); }
    @PostMapping("/{id}/role") public UserView changeRole(@PathVariable Long id, @Valid @RequestBody ChangeRoleRequest request, Authentication auth) { return service.changeRole(id, request, auth); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id, Authentication auth) { service.delete(id, auth); }
}