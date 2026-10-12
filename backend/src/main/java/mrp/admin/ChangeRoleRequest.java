package mrp.admin;
import jakarta.validation.constraints.NotNull;
import mrp.user.UserRole;
public record ChangeRoleRequest(@NotNull UserRole role) { }