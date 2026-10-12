package mrp.auth;

import mrp.user.UserRole;

public record UserView(Long id, String username, UserRole role, Long profileId, String fullName,
                       String email, String phone, String address, boolean active) { }