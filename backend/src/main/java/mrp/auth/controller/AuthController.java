package mrp.auth.controller;


import mrp.auth.dto.LoginRequest;
import mrp.auth.dto.LoginResponse;
import mrp.auth.dto.UserView;
import mrp.auth.dto.UpdateProfileRequest;
import mrp.auth.dto.ChangePasswordRequest;
import mrp.security.CurrentUserResolver;
import mrp.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;
    private final CurrentUserResolver currentUser;
    public AuthController(AuthService service, CurrentUserResolver currentUser) { this.service = service; this.currentUser = currentUser; }

    @PostMapping("/login")
    @io.swagger.v3.oas.annotations.security.SecurityRequirements()
    public LoginResponse login(@Valid @RequestBody LoginRequest request) { return service.login(request); }

    @GetMapping("/me")
    public UserView me(Authentication authentication) {
        return service.meByUsername(authentication.getName());
    }

    @PutMapping("/me")
    public UserView updateProfile(@Valid @RequestBody UpdateProfileRequest request, Authentication authentication) {
        return service.updateProfile(currentUser.resolve(authentication).userId(), request);
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request, Authentication authentication) {
        service.changePassword(currentUser.resolve(authentication).userId(), request);
    }
}
