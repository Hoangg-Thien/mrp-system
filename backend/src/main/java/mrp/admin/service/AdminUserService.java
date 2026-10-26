package mrp.admin.service;


import mrp.admin.dto.ChangeRoleRequest;
import mrp.admin.dto.CreateUserRequest;
import mrp.auth.dto.UserView;
import mrp.auth.service.AuthService;
import mrp.common.exception.BusinessException;
import mrp.common.exception.ResourceNotFoundException;
import mrp.user.entity.AdminProfile;
import mrp.user.entity.Staff;
import mrp.user.entity.User;
import mrp.user.entity.UserRole;
import mrp.user.entity.WarehouseManager;
import mrp.user.repository.AdminProfileRepository;
import mrp.user.repository.StaffRepository;
import mrp.user.repository.UserRepository;
import mrp.user.repository.WarehouseManagerRepository;
import mrp.user.service.UserReferenceChecker;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {
    private final UserRepository users;
    private final StaffRepository staff;
    private final WarehouseManagerRepository managers;
    private final AdminProfileRepository admins;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final UserReferenceChecker referenceChecker;

    public AdminUserService(UserRepository users, StaffRepository staff, WarehouseManagerRepository managers,
                            AdminProfileRepository admins, PasswordEncoder passwordEncoder, AuthService authService,
                            UserReferenceChecker referenceChecker) {
        this.users=users; this.staff=staff; this.managers=managers; this.admins=admins;
        this.passwordEncoder=passwordEncoder; this.authService=authService;
        this.referenceChecker=referenceChecker;
    }

    @Transactional
    public UserView create(CreateUserRequest request) {
        if (users.existsByUsername(request.username())) throw new BusinessException("Username đã tồn tại");
        User user = users.save(new User(request.username(), passwordEncoder.encode(request.password()), request.role()));
        createProfile(user.getId(), request);
        return authService.view(user);
    }

    @Transactional(readOnly = true)
    public Page<UserView> list(UserRole role, Boolean active, String keyword, int page, int size, String sortField, String direction) {
        if (size < 1 || size > 100) throw new BusinessException("Kích thước trang phải từ 1 đến 100");
        if (!sortField.matches("username|createdAt|role|active")) throw new BusinessException("Trường sắp xếp không hợp lệ");
        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortField));
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        return users.search(role, active, normalizedKeyword, pageable).map(authService::view);
    }

    @Transactional(readOnly = true)
    public UserView detail(Long id) { return authService.view(find(id)); }

    @Transactional
    public UserView lock(Long id, Authentication actor) {
        User user = findForUpdate(id);
        checkNotSelf(user, actor);
        if (user.getRole() == UserRole.ADMIN && user.isActive() && users.countByRoleAndActive(UserRole.ADMIN, true) <= 1) {
            throw new BusinessException("Không thể khóa admin cuối cùng đang hoạt động");
        }
        user.setActive(false);
        return authService.view(user);
    }

    @Transactional
    public UserView unlock(Long id) { User user=findForUpdate(id); user.setActive(true); return authService.view(user); }

    @Transactional
    public UserView changeRole(Long id, ChangeRoleRequest request, Authentication actor) {
        User user = findForUpdate(id);
        checkNotSelf(user, actor);
        if (user.getRole() == request.role()) return authService.view(user);
        if (user.getRole() == UserRole.ADMIN && user.isActive() && users.countByRoleAndActive(UserRole.ADMIN, true) <= 1) {
            throw new BusinessException("Không thể hạ quyền admin cuối cùng đang hoạt động");
        }
        UserView oldProfile = authService.view(user);
        if (referenceChecker.isReferenced(user, oldProfile.profileId())) {
            throw new BusinessException("Không thể đổi role cho tài khoản đã phát sinh dữ liệu; hãy khóa tài khoản");
        }
        deleteProfile(user.getId(), user.getRole());
        users.flush();
        user.setRole(request.role());
        users.flush();
        createProfile(user.getId(), new CreateUserRequest(user.getUsername(), "ignored", request.role(), oldProfile.fullName(), oldProfile.email(), oldProfile.phone(), oldProfile.address()));
        return authService.view(user);
    }

    private User find(Long id) { return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản")); }
    private User findForUpdate(Long id) { return users.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản")); }
    private void checkNotSelf(User user, Authentication actor) { if (user.getUsername().equals(actor.getName())) throw new BusinessException("Không thể thao tác trên tài khoản hiện tại"); }
    private void createProfile(Long userId, CreateUserRequest r) {
        switch (r.role()) {
            case STAFF -> staff.save(new Staff(userId, r.fullName(), r.email(), r.phone(), r.address()));
            case WAREHOUSE_MANAGER -> managers.save(new WarehouseManager(userId, r.fullName(), r.email(), r.phone(), r.address()));
            case ADMIN -> admins.save(new AdminProfile(userId, r.fullName(), r.email(), r.phone(), r.address()));
        }
    }
    private void deleteProfile(Long userId, UserRole role) {
        switch (role) {
            case STAFF -> staff.deleteByUserId(userId);
            case WAREHOUSE_MANAGER -> managers.deleteByUserId(userId);
            case ADMIN -> admins.deleteByUserId(userId);
        }
    }
}
