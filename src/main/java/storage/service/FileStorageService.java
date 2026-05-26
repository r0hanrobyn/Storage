package storage.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import storage.exception.FileStorageException;
import storage.exception.ResourceNotFoundException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Set;
import storage.model.FileMetadata;
import storage.model.User;
import storage.repository.FileMetadataRepository;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileStorageService {

    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            ".exe", ".sh", ".bat", ".cmd", ".ps1", ".php", ".jsp", ".py", ".rb"
    );

    private final FileMetadataRepository fileMetadataRepository;

    @Value("${storage.location}")
    private String storageLocation;

    @Value("${storage.quota-bytes}")
    private long quotaBytes;

    // ── Upload ────────────────────────────────────────────────────

    public FileMetadata store(MultipartFile file, User owner) {
        if (file.isEmpty()) {
            throw new FileStorageException("Cannot store an empty file");
        }

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        String lowerName = originalFilename.toLowerCase();
        String extension = lowerName.contains(".")
                ? lowerName.substring(lowerName.lastIndexOf("."))
                : "";

        if (BLOCKED_EXTENSIONS.contains(extension)) {
            throw new FileStorageException("File type '" + extension + "' is not allowed");
        }

        long used = fileMetadataRepository.sumSizeByOwner(owner);
        if (used + file.getSize() > quotaBytes) {
            throw new FileStorageException("Storage quota exceeded. Used: " + used + " bytes, quota: " + quotaBytes + " bytes");
        }

        try {
            Path uploadDir = Paths.get(storageLocation);
            Files.createDirectories(uploadDir);

            String storedFilename = UUID.randomUUID() + extension;
            Path destination = uploadDir.resolve(storedFilename);
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

            FileMetadata metadata = FileMetadata.builder()
                    .originalFilename(originalFilename)
                    .storedFilename(storedFilename)
                    .contentType(file.getContentType())
                    .size(file.getSize())
                    .owner(owner)
                    .build();

            return fileMetadataRepository.save(metadata);
        } catch (IOException ex) {
            throw new FileStorageException("Failed to store file", ex);
        }
    }

    // ── List (active files) ───────────────────────────────────────

    public Page<FileMetadata> listFiles(User owner, String name, boolean starredOnly, Pageable pageable) {
        if (starredOnly) {
            if (name != null && !name.isBlank()) {
                return fileMetadataRepository
                        .findAllByOwnerAndDeletedFalseAndStarredTrueAndOriginalFilenameContainingIgnoreCase(owner, name, pageable);
            }
            return fileMetadataRepository.findAllByOwnerAndDeletedFalseAndStarredTrue(owner, pageable);
        }
        if (name != null && !name.isBlank()) {
            return fileMetadataRepository
                    .findAllByOwnerAndDeletedFalseAndOriginalFilenameContainingIgnoreCase(owner, name, pageable);
        }
        return fileMetadataRepository.findAllByOwnerAndDeletedFalse(owner, pageable);
    }

    // ── Bin ───────────────────────────────────────────────────────

    public Page<FileMetadata> listBin(User owner, Pageable pageable) {
        return fileMetadataRepository.findAllByOwnerAndDeletedTrue(owner, pageable);
    }

    // ── Star toggle ───────────────────────────────────────────────

    public FileMetadata toggleStar(Long fileId, User owner) {
        FileMetadata metadata = fileMetadataRepository.findByIdAndOwnerAndDeletedFalse(fileId, owner)
                .orElseThrow(() -> new ResourceNotFoundException("File not found or access denied"));
        metadata.setStarred(!metadata.isStarred());
        return fileMetadataRepository.save(metadata);
    }

    // ── Soft delete ───────────────────────────────────────────────

    public void delete(Long fileId, User owner) {
        FileMetadata metadata = fileMetadataRepository.findByIdAndOwnerAndDeletedFalse(fileId, owner)
                .orElseThrow(() -> new ResourceNotFoundException("File not found or access denied"));
        metadata.setDeleted(true);
        metadata.setDeletedAt(Instant.now());
        fileMetadataRepository.save(metadata);
    }

    // ── Restore from bin ──────────────────────────────────────────

    public FileMetadata restore(Long fileId, User owner) {
        FileMetadata metadata = fileMetadataRepository.findByIdAndOwnerAndDeletedTrue(fileId, owner)
                .orElseThrow(() -> new ResourceNotFoundException("File not found in bin"));
        metadata.setDeleted(false);
        metadata.setDeletedAt(null);
        return fileMetadataRepository.save(metadata);
    }

    // ── Permanent delete ──────────────────────────────────────────

    public void permanentDelete(Long fileId, User owner) {
        FileMetadata metadata = fileMetadataRepository.findByIdAndOwnerAndDeletedTrue(fileId, owner)
                .orElseThrow(() -> new ResourceNotFoundException("File not found in bin"));

        try {
            Path filePath = Paths.get(storageLocation).resolve(metadata.getStoredFilename());
            Files.deleteIfExists(filePath);
        } catch (IOException ex) {
            throw new FileStorageException("Failed to delete file from disk", ex);
        }

        fileMetadataRepository.delete(metadata);
    }

    // ── Download ──────────────────────────────────────────────────

    public Resource loadAsResource(Long fileId, User owner) {
        FileMetadata metadata = fileMetadataRepository.findByIdAndOwnerAndDeletedFalse(fileId, owner)
                .orElseThrow(() -> new ResourceNotFoundException("File not found or access denied"));
        return loadAsResourceDirect(metadata);
    }

    public Resource loadAsResourceDirect(FileMetadata metadata) {
        try {
            Path filePath = Paths.get(storageLocation).resolve(metadata.getStoredFilename());
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new FileStorageException("File is not readable: " + metadata.getOriginalFilename());
            }
            return resource;
        } catch (MalformedURLException ex) {
            throw new FileStorageException("Could not resolve file path", ex);
        }
    }

    public String getOriginalFilename(Long fileId, User owner) {
        return fileMetadataRepository.findByIdAndOwnerAndDeletedFalse(fileId, owner)
                .map(FileMetadata::getOriginalFilename)
                .orElseThrow(() -> new ResourceNotFoundException("File not found or access denied"));
    }

    // ── Quota ─────────────────────────────────────────────────────

    public long getUsedBytes(User owner) {
        return fileMetadataRepository.sumSizeByOwner(owner);
    }

    public long getQuotaBytes() {
        return quotaBytes;
    }
}
