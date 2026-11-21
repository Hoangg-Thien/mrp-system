package mrp.security;

public record CurrentUser(Long userId, Long profileId, String username) { }