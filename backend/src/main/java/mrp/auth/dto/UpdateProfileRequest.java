package mrp.auth.dto;

import jakarta.validation.constraints.*;

public record UpdateProfileRequest(
        @NotBlank @Size(max = 150) String fullName,
        @Email @Size(max = 150) String email,
        @Size(max = 20) String phone,
        @Size(max = 255) String address) { }
