package storage.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import storage.exception.ResourceNotFoundException;
import storage.model.FileMetadata;
import storage.model.ShareLink;
import storage.model.User;
import storage.repository.FileMetadataRepository;
import storage.repository.ShareLinkRepository;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShareLinkService {

    private final ShareLinkRepository shareLinkRepository;
    private final FileMetadataRepository fileMetadataRepository;

    public ShareLink createShareLink(Long fileId, User owner, Integer expiryHours) {
        FileMetadata file = fileMetadataRepository.findByIdAndOwner(fileId, owner)
                .orElseThrow(() -> new ResourceNotFoundException("File not found or access denied"));

        shareLinkRepository.findByFile(file).ifPresent(shareLinkRepository::delete);

        Instant expiresAt = expiryHours != null
                ? Instant.now().plusSeconds(expiryHours * 3600L)
                : null;

        return shareLinkRepository.save(ShareLink.builder()
                .token(UUID.randomUUID().toString())
                .file(file)
                .expiresAt(expiresAt)
                .build());
    }

    public FileMetadata resolveToken(String token) {
        ShareLink link = shareLinkRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Share link not found"));

        if (link.isExpired()) {
            shareLinkRepository.delete(link);
            throw new ResourceNotFoundException("Share link has expired");
        }
        return link.getFile();
    }

    public void deleteShareLink(Long fileId, User owner) {
        FileMetadata file = fileMetadataRepository.findByIdAndOwner(fileId, owner)
                .orElseThrow(() -> new ResourceNotFoundException("File not found or access denied"));
        shareLinkRepository.findByFile(file).ifPresent(shareLinkRepository::delete);
    }
}
