package storage.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import storage.dto.FileResponse;
import storage.dto.ShareLinkResponse;
import storage.dto.ShareRequest;
import storage.model.FileMetadata;
import storage.model.ShareLink;
import storage.model.User;
import storage.service.FileStorageService;
import storage.service.ShareLinkService;
import storage.service.UserService;

@Tag(name = "Files", description = "Upload, list, download, delete, share, and star files")
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;
    private final ShareLinkService shareLinkService;
    private final UserService userService;

    // ── Upload ────────────────────────────────────────────────────

    @Operation(summary = "Upload a file")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/upload")
    public ResponseEntity<FileResponse> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {

        User owner = userService.findByUsername(userDetails.getUsername());
        FileMetadata metadata = fileStorageService.store(file, owner);
        return ResponseEntity.ok(FileResponse.from(metadata));
    }

    // ── List active files (paginated, searchable, filterable) ─────

    @Operation(summary = "List files (paginated; ?name=search; ?starred=true)")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public ResponseEntity<Page<FileResponse>> listFiles(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "false") boolean starred,
            @PageableDefault(size = 20) Pageable pageable) {

        User owner = userService.findByUsername(userDetails.getUsername());
        Page<FileResponse> files = fileStorageService.listFiles(owner, name, starred, pageable)
                .map(FileResponse::from);
        return ResponseEntity.ok(files);
    }

    // ── Download ──────────────────────────────────────────────────

    @Operation(summary = "Download a file by ID")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        User owner = userService.findByUsername(userDetails.getUsername());
        Resource resource = fileStorageService.loadAsResource(id, owner);
        String originalFilename = fileStorageService.getOriginalFilename(id, owner);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + originalFilename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    // ── Soft delete (move to bin) ─────────────────────────────────

    @Operation(summary = "Move a file to the bin")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        User owner = userService.findByUsername(userDetails.getUsername());
        fileStorageService.delete(id, owner);
        return ResponseEntity.noContent().build();
    }

    // ── Star toggle ───────────────────────────────────────────────

    @Operation(summary = "Toggle the starred flag on a file")
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/{id}/star")
    public ResponseEntity<FileResponse> toggleStar(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        User owner = userService.findByUsername(userDetails.getUsername());
        FileMetadata updated = fileStorageService.toggleStar(id, owner);
        return ResponseEntity.ok(FileResponse.from(updated));
    }

    // ── Bin ───────────────────────────────────────────────────────

    @Operation(summary = "List files in the bin")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/bin")
    public ResponseEntity<Page<FileResponse>> listBin(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20) Pageable pageable) {

        User owner = userService.findByUsername(userDetails.getUsername());
        Page<FileResponse> files = fileStorageService.listBin(owner, pageable).map(FileResponse::from);
        return ResponseEntity.ok(files);
    }

    @Operation(summary = "Restore a file from the bin")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{id}/restore")
    public ResponseEntity<FileResponse> restore(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        User owner = userService.findByUsername(userDetails.getUsername());
        return ResponseEntity.ok(FileResponse.from(fileStorageService.restore(id, owner)));
    }

    @Operation(summary = "Permanently delete a file from the bin")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<Void> permanentDelete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        User owner = userService.findByUsername(userDetails.getUsername());
        fileStorageService.permanentDelete(id, owner);
        return ResponseEntity.noContent().build();
    }

    // ── Share ─────────────────────────────────────────────────────

    @Operation(summary = "Create or refresh a share link for a file")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{id}/share")
    public ResponseEntity<ShareLinkResponse> createShare(
            @PathVariable Long id,
            @RequestBody(required = false) ShareRequest req,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {

        User owner = userService.findByUsername(userDetails.getUsername());
        Integer expiryHours = req != null ? req.expiryHours() : null;
        ShareLink link = shareLinkService.createShareLink(id, owner, expiryHours);

        String baseUrl = httpRequest.getScheme() + "://" + httpRequest.getServerName()
                + (httpRequest.getServerPort() != 80 && httpRequest.getServerPort() != 443
                   ? ":" + httpRequest.getServerPort() : "");

        return ResponseEntity.ok(ShareLinkResponse.from(link, baseUrl));
    }

    @Operation(summary = "Revoke a share link for a file")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{id}/share")
    public ResponseEntity<Void> revokeShare(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        User owner = userService.findByUsername(userDetails.getUsername());
        shareLinkService.deleteShareLink(id, owner);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Download a file via public share token (no auth required)")
    @GetMapping("/shared/{token}")
    public ResponseEntity<Resource> downloadShared(@PathVariable String token) {
        FileMetadata metadata = shareLinkService.resolveToken(token);
        Resource resource = fileStorageService.loadAsResourceDirect(metadata);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + metadata.getOriginalFilename() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
