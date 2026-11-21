package mrp.auth.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserView user) { }