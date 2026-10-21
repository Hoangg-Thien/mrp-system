package mrp.auth.dto;



import mrp.user.entity.UserRole;
public record UserView(Long id, String username, UserRole role, Long profileId, String fullName,
                       String email, String phone, String address, boolean active) { }