package storage.dto;

import lombok.Builder;
import lombok.Data;
import storage.model.FileMetadata;

import java.time.LocalDateTime;

@Data
@Builder
public class FileResponse {

    private Long id;
    private String originalFilename;
    private String contentType;
    private long size;
    private LocalDateTime uploadedAt;

    public static FileResponse from(FileMetadata meta) {
        return FileResponse.builder()
                .id(meta.getId())
                .originalFilename(meta.getOriginalFilename())
                .contentType(meta.getContentType())
                .size(meta.getSize())
                .uploadedAt(meta.getUploadedAt())
                .build();
    }
}
