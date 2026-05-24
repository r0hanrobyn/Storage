package storage.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import storage.dto.FileResponse;
import storage.model.FileMetadata;
import storage.model.User;
import storage.service.FileStorageService;
import storage.service.UserService;

@Tag(name = "Files", description = "Upload, list, download, and delete files")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;
    private final UserService userService;

    @Operation(summary = "Upload a file")
    @PostMapping("/upload")
    public ResponseEntity<FileResponse> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {

        User owner = userService.findByUsername(userDetails.getUsername());
        FileMetadata metadata = fileStorageService.store(file, owner);
        return ResponseEntity.ok(FileResponse.from(metadata));
    }

    @Operation(summary = "List all files owned by the current user (paginated)")
    @GetMapping
    public ResponseEntity<Page<FileResponse>> listFiles(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20) Pageable pageable) {

        User owner = userService.findByUsername(userDetails.getUsername());
        Page<FileResponse> files = fileStorageService.listFiles(owner, pageable)
                .map(FileResponse::from);
        return ResponseEntity.ok(files);
    }

    @Operation(summary = "Download a file by ID")
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

    @Operation(summary = "Delete a file by ID")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        User owner = userService.findByUsername(userDetails.getUsername());
        fileStorageService.delete(id, owner);
        return ResponseEntity.noContent().build();
    }
}
