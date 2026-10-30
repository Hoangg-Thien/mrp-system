package mrp.auth.dto;

import jakarta.validation.constraints.*;

public record ChangePasswordRequest(
        @NotBlank @Size(max = 100) String currentPassword,
        @NotBlank @Size(min = 8, max = 100) String newPassword,
        @NotBlank @Size(max = 100) String confirmPassword) { }
