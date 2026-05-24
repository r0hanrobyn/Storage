package storage.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import storage.exception.FileStorageException;
import storage.exception.ResourceNotFoundException;

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
import java.util.List;
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

    public List<FileMetadata> listFiles(User owner) {
        return fileMetadataRepository.findAllByOwner(owner);
    }

    public Resource loadAsResource(Long fileId, User owner) {
        FileMetadata metadata = fileMetadataRepository.findByIdAndOwner(fileId, owner)
                .orElseThrow(() -> new ResourceNotFoundException("File not found or access denied"));

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
        return fileMetadataRepository.findByIdAndOwner(fileId, owner)
                .map(FileMetadata::getOriginalFilename)
                .orElseThrow(() -> new ResourceNotFoundException("File not found or access denied"));
    }

    public void delete(Long fileId, User owner) {
        FileMetadata metadata = fileMetadataRepository.findByIdAndOwner(fileId, owner)
                .orElseThrow(() -> new ResourceNotFoundException("File not found or access denied"));

        try {
            Path filePath = Paths.get(storageLocation).resolve(metadata.getStoredFilename());
            Files.deleteIfExists(filePath);
        } catch (IOException ex) {
            throw new FileStorageException("Failed to delete file", ex);
        }

        fileMetadataRepository.delete(metadata);
    }
}
