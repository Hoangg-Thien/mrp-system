package mrp.config;

import mrp.admin.AdminUserService;
import mrp.admin.CreateUserRequest;
import mrp.user.UserRepository;
import mrp.user.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BootstrapAdminRunner implements ApplicationRunner {
    private final UserRepository users;
    private final AdminUserService adminUserService;
    private final String username;
    private final String password;
    private final String fullName;

    public BootstrapAdminRunner(UserRepository users, AdminUserService adminUserService,
                                @Value("${app.bootstrap-admin.username:}") String username,
                                @Value("${app.bootstrap-admin.password:}") String password,
                                @Value("${app.bootstrap-admin.full-name:System Administrator}") String fullName) {
        this.users=users; this.adminUserService=adminUserService; this.username=username; this.password=password; this.fullName=fullName;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() == 0 && !username.isBlank() && password.length() >= 8) {
            adminUserService.create(new CreateUserRequest(username, password, UserRole.ADMIN, fullName, null, null, null));
        }
    }
}