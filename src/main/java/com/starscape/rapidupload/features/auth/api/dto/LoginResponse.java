package com.starscape.rapidupload.features.auth.api.dto;

public record LoginResponse(
    String userId,
    String email,
    String token,
    String tokenType,
    long expiresIn
) {
    public static LoginResponse of(String userId, String email, String token, long expiresInMs) {
        return new LoginResponse(userId, email, token, "Bearer", expiresInMs);
    }
}

