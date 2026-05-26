package storage.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import storage.exception.FileStorageException;
import storage.exception.ResourceNotFoundException;
import storage.model.FileMetadata;
import storage.model.User;
import storage.repository.FileMetadataRepository;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileStorageServiceTest {

    @Mock private FileMetadataRepository repository;
    @InjectMocks private FileStorageService service;

    @TempDir Path tempDir;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "storageLocation", tempDir.toString());
        ReflectionTestUtils.setField(service, "quotaBytes", 524_288_000L); // 500 MB
    }

    @Test
    void store_emptyFile_throws() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);
        User owner = User.builder().username("alice").build();

        assertThatThrownBy(() -> service.store(file, owner))
                .isInstanceOf(FileStorageException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void store_blockedExtension_throws() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("virus.exe");
        User owner = User.builder().username("alice").build();

        assertThatThrownBy(() -> service.store(file, owner))
                .isInstanceOf(FileStorageException.class)
                .hasMessageContaining(".exe");
    }

    @Test
    void store_validFile_savesAndReturnsMetadata() throws Exception {
        byte[] content = "hello".getBytes();
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("notes.txt");
        when(file.getContentType()).thenReturn("text/plain");
        when(file.getSize()).thenReturn((long) content.length);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(content));

        User owner = User.builder().id(1L).username("alice").build();
        when(repository.sumSizeByOwner(owner)).thenReturn(0L);
        FileMetadata saved = FileMetadata.builder()
                .id(1L).originalFilename("notes.txt").storedFilename("uuid.txt")
                .contentType("text/plain").size(5L).owner(owner).build();
        when(repository.save(any())).thenReturn(saved);

        FileMetadata result = service.store(file, owner);

        assertThat(result.getOriginalFilename()).isEqualTo("notes.txt");
        verify(repository).save(any());
    }

    @Test
    void loadAsResource_notFound_throws() {
        User owner = User.builder().id(1L).username("alice").build();
        when(repository.findByIdAndOwner(99L, owner)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadAsResource(99L, owner))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getOriginalFilename_notFound_throws() {
        User owner = User.builder().id(1L).username("alice").build();
        when(repository.findByIdAndOwner(99L, owner)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOriginalFilename(99L, owner))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_notFound_throws() {
        User owner = User.builder().id(1L).username("alice").build();
        when(repository.findByIdAndOwner(99L, owner)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L, owner))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
