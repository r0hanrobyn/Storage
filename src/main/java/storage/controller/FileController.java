package storage.controller;

import lombok.RequiredArgsConstructor;
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

import java.util.List;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;
    private final UserService userService;

    @PostMapping("/upload")
    public ResponseEntity<FileResponse> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {

        User owner = userService.findByUsername(userDetails.getUsername());
        FileMetadata metadata = fileStorageService.store(file, owner);
        return ResponseEntity.ok(FileResponse.from(metadata));
    }

    @GetMapping
    public ResponseEntity<List<FileResponse>> listFiles(
            @AuthenticationPrincipal UserDetails userDetails) {

        User owner = userService.findByUsername(userDetails.getUsername());
        List<FileResponse> files = fileStorageService.listFiles(owner)
                .stream()
                .map(FileResponse::from)
                .toList();
        return ResponseEntity.ok(files);
    }

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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        User owner = userService.findByUsername(userDetails.getUsername());
        fileStorageService.delete(id, owner);
        return ResponseEntity.noContent().build();
    }
}
