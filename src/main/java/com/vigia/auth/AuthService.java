package com.vigia.auth;

import com.vigia.auth.AuthDtos.LoginRequest;
import com.vigia.auth.AuthDtos.RegisterRequest;
import com.vigia.auth.AuthDtos.TokenResponse;
import com.vigia.common.ApiException;
import com.vigia.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "E-mail já cadastrado");
        }
        User user = users.save(new User(request.name().trim(), email, passwordEncoder.encode(request.password())));
        return tokenFor(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = users.findByEmailIgnoreCase(request.email().trim())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos"));
        return tokenFor(user);
    }

    private TokenResponse tokenFor(User user) {
        return new TokenResponse(jwtService.generate(user), "Bearer", jwtService.expirationSeconds());
    }
}
