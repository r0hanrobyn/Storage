package storage.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import storage.dto.AuthResponse;
import storage.dto.LoginRequest;
import storage.dto.RefreshTokenRequest;
import storage.dto.RegisterRequest;
import storage.exception.ResourceNotFoundException;
import storage.model.RefreshToken;
import storage.model.User;
import storage.security.JwtUtils;
import storage.service.RefreshTokenService;
import storage.service.UserService;

@Tag(name = "Authentication", description = "Register and log in to receive a JWT")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;

    @Operation(summary = "Register a new user")
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.registerUser(request.getUsername(), request.getEmail(), request.getPassword());
        String accessToken = jwtUtils.generateToken(user.getUsername());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);
        return ResponseEntity.ok(new AuthResponse(accessToken, user.getUsername(), refreshToken.getToken()));
    }

    @Operation(summary = "Log in and receive a JWT")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        User user = userService.findByUsername(auth.getName());
        String accessToken = jwtUtils.generateToken(auth.getName());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);
        return ResponseEntity.ok(new AuthResponse(accessToken, auth.getName(), refreshToken.getToken()));
    }

    @Operation(summary = "Issue a new access token using a refresh token")
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        RefreshToken refreshToken = refreshTokenService.findByToken(request.refreshToken())
                .orElseThrow(() -> new ResourceNotFoundException("Refresh token not found"));
        refreshTokenService.verifyExpiration(refreshToken);
        String newAccessToken = jwtUtils.generateToken(refreshToken.getUser().getUsername());
        return ResponseEntity.ok(new AuthResponse(newAccessToken, refreshToken.getUser().getUsername(), refreshToken.getToken()));
    }

    @Operation(summary = "Log out and invalidate the refresh token")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.findByUsername(userDetails.getUsername());
        refreshTokenService.deleteByUser(user);
        return ResponseEntity.noContent().build();
    }
}
