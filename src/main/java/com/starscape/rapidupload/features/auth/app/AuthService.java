package com.starscape.rapidupload.features.auth.app;

import com.starscape.rapidupload.common.security.JwtTokenProvider;
import com.starscape.rapidupload.features.auth.api.dto.LoginRequest;
import com.starscape.rapidupload.features.auth.api.dto.LoginResponse;
import com.starscape.rapidupload.features.auth.api.dto.RegisterRequest;
import com.starscape.rapidupload.features.auth.domain.User;
import com.starscape.rapidupload.features.auth.domain.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AuthService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final long tokenExpirationMs;
    
    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider,
            @Value("${app.security.jwt.expiration-ms}") long tokenExpirationMs) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.tokenExpirationMs = tokenExpirationMs;
    }
    
    @Transactional
    public LoginResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already registered");
        }
        
        String userId = "user_" + UUID.randomUUID().toString().replace("-", "");
        String passwordHash = passwordEncoder.encode(request.password());
        
        User user = new User(userId, request.email(), passwordHash);
        userRepository.save(user);
        
        List<String> scopes = List.of("photos:read", "photos:write");
        String token = tokenProvider.generateToken(userId, request.email(), scopes);
        
        return LoginResponse.of(userId, request.email(), token, tokenExpirationMs);
    }
    
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        
        if (!user.isActive()) {
            throw new IllegalStateException("Account is not active");
        }
        
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        
        List<String> scopes = List.of("photos:read", "photos:write");
        String token = tokenProvider.generateToken(user.getUserId(), user.getEmail(), scopes);
        
        return LoginResponse.of(user.getUserId(), user.getEmail(), token, tokenExpirationMs);
    }
}

