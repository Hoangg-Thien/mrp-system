package mrp.user.dto;

/** id is staff.id (profile), not users.id. */
public record StaffView(Long id, String fullName) { }