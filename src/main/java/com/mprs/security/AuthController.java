package com.mprs.security;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * Handles authentication requests.
 * Issues JWT tokens on successful login.
 *
 * This is the only public endpoint in the system —
 * all others require a valid JWT in the Authorization header.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login and token issuance")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider      jwtTokenProvider;

    // ── Request / Response DTOs (inner classes for simplicity) ───

    /**
     * Login request payload.
     */
    public record LoginRequest(
            @NotBlank(message = "Username is required") String username,
            @NotBlank(message = "Password is required") String password
    ) {}

    /**
     * Login response payload — contains the JWT token and metadata.
     */
    public record LoginResponse(
            String   token,
            String   type,
            String   username,
            String   role,
            long     expiresIn,
            Instant  issuedAt
    ) {}

    // ── Endpoints ────────────────────────────────────────────────

    /**
     * POST /api/v1/auth/login
     *
     * Authenticates user credentials and returns a signed JWT token.
     * Use the token in subsequent requests as:
     *   Authorization: Bearer <token>
     *
     * @param request login credentials
     * @return JWT token with metadata
     */
    @PostMapping("/login")
    @Operation(
            summary     = "Login",
            description = "Authenticate with username and password to receive a JWT token"
    )
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {

        log.info("Login attempt for user: {}", request.username());

        // 1. Delegate to Spring Security — throws AuthenticationException if invalid
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.username(),
                        request.password()
                )
        );

        // 2. Generate JWT token from the authenticated principal
        String token = jwtTokenProvider.generateToken(authentication);

        // 3. Extract role for the response body
        String role = authentication.getAuthorities()
                .stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElse("UNKNOWN");

        log.info("Login successful for user: {} with role: {}", request.username(), role);

        return ResponseEntity.ok(new LoginResponse(
                token,
                "Bearer",
                request.username(),
                role,
                86400000L,        // 24 hours in ms — matches jwt.expiration-ms
                Instant.now()
        ));
    }
}