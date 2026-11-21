package mrp.admin.dto;

import mrp.user.entity.UserRole;
import jakarta.validation.constraints.NotNull;
public record ChangeRoleRequest(@NotNull UserRole role) { }