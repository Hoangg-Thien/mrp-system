package mrp.auth.service;


import mrp.auth.dto.LoginRequest;
import mrp.auth.dto.LoginResponse;
import mrp.auth.dto.UserView;
import mrp.auth.dto.UpdateProfileRequest;
import mrp.auth.dto.ChangePasswordRequest;
import mrp.common.exception.BadRequestException;
import mrp.common.exception.BusinessException;
import mrp.common.exception.InvalidCredentialsException;
import mrp.user.entity.User;
import mrp.user.repository.AdminProfileRepository;
import mrp.user.repository.StaffRepository;
import mrp.user.repository.UserRepository;
import mrp.user.repository.WarehouseManagerRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final StaffRepository staff;
    private final WarehouseManagerRepository managers;
    private final AdminProfileRepository admins;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                       StaffRepository staff, WarehouseManagerRepository managers, AdminProfileRepository admins) {
        this.users=users; this.passwordEncoder=passwordEncoder; this.jwtService=jwtService;
        this.staff=staff; this.managers=managers; this.admins=admins;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = users.findByUsername(request.username()).orElse(null);
        if (user == null || !user.isActive() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Username hoặc mật khẩu không đúng");
        }
        return new LoginResponse(jwtService.createToken(user, profileId(user)), "Bearer", jwtService.getExpirationSeconds(), view(user));
    }

    @Transactional(readOnly = true)
    public UserView me(Long userId) {
        User user = users.findById(userId).orElseThrow(() -> new BusinessException("Tài khoản không còn tồn tại"));
        if (!user.isActive()) throw new BusinessException("Tài khoản đã bị khóa");
        return view(user);
    }

    @Transactional(readOnly = true)
    public UserView meByUsername(String username) {
        User user = users.findByUsername(username).orElseThrow(() -> new BusinessException("Tài khoản không còn tồn tại"));
        if (!user.isActive()) throw new BusinessException("Tài khoản đã bị khóa");
        return view(user);
    }

    public UserView view(User user) {
        return switch (user.getRole()) {
            case STAFF -> staff.findByUserId(user.getId()).map(p -> new UserView(user.getId(), user.getUsername(), user.getRole(), p.getId(), p.getFullName(), p.getEmail(), p.getPhone(), p.getAddress(), user.isActive(), user.getCreatedAt())).orElseThrow();
            case WAREHOUSE_MANAGER -> managers.findByUserId(user.getId()).map(p -> new UserView(user.getId(), user.getUsername(), user.getRole(), p.getId(), p.getFullName(), p.getEmail(), p.getPhone(), p.getAddress(), user.isActive(), user.getCreatedAt())).orElseThrow();
            case ADMIN -> admins.findByUserId(user.getId()).map(p -> new UserView(user.getId(), user.getUsername(), user.getRole(), p.getId(), p.getFullName(), p.getEmail(), p.getPhone(), p.getAddress(), user.isActive(), user.getCreatedAt())).orElseThrow();
        };
    }

    private Long profileId(User user) { return view(user).profileId(); }

    @Transactional
    public UserView updateProfile(Long userId, UpdateProfileRequest request) {
        User user = activeUserForUpdate(userId);
        String name = request.fullName().trim();
        String email = clean(request.email()), phone = clean(request.phone()), address = clean(request.address());
        switch (user.getRole()) {
            case ADMIN -> admins.findByUserId(userId).orElseThrow().updateProfile(name, email, phone, address);
            case STAFF -> staff.findByUserId(userId).orElseThrow().updateProfile(name, email, phone, address);
            case WAREHOUSE_MANAGER -> managers.findByUserId(userId).orElseThrow().updateProfile(name, email, phone, address);
        }
        return view(user);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = activeUserForUpdate(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Mật khẩu hiện tại không đúng");
        }
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BadRequestException("Nhập lại mật khẩu mới không khớp");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Mật khẩu mới phải khác mật khẩu hiện tại");
        }
        // BCrypt only processes up to 72 bytes; reject instead of silently truncating.
        if (request.newPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new BadRequestException("Mật khẩu mới tối đa 72 byte UTF-8");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    private User activeUserForUpdate(Long userId) {
        User user = users.findByIdForUpdate(userId).orElseThrow(() -> new InvalidCredentialsException("Tài khoản không còn tồn tại"));
        if (!user.isActive()) throw new InvalidCredentialsException("Tài khoản đã bị khóa");
        return user;
    }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
