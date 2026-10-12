package mrp.auth;

public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserView user) { }