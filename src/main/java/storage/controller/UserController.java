package storage.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import storage.dto.ChangePasswordRequest;
import storage.dto.QuotaResponse;
import storage.dto.UserProfileResponse;
import storage.model.User;
import storage.service.FileStorageService;
import storage.service.UserService;

@Tag(name = "Users", description = "User profile, quota, and password management")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final FileStorageService fileStorageService;

    @Operation(summary = "Get the current user's profile")
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> me(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                UserProfileResponse.from(userService.findByUsername(userDetails.getUsername()))
        );
    }

    @Operation(summary = "Get the current user's storage quota")
    @GetMapping("/me/quota")
    public ResponseEntity<QuotaResponse> quota(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.findByUsername(userDetails.getUsername());
        long used  = fileStorageService.getUsedBytes(user);
        long total = fileStorageService.getQuotaBytes();
        return ResponseEntity.ok(QuotaResponse.of(used, total));
    }

    @Operation(summary = "Change the current user's password")
    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest req) {

        User user = userService.findByUsername(userDetails.getUsername());
        userService.changePassword(user, req.currentPassword(), req.newPassword());
        return ResponseEntity.noContent().build();
    }
}
