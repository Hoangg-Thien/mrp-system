package mrp.user;

/** id is staff.id (profile), not users.id. */
public record StaffView(Long id, String fullName) { }