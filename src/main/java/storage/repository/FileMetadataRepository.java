package storage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import storage.model.FileMetadata;
import storage.model.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {

    List<FileMetadata> findAllByOwner(User owner);

    Optional<FileMetadata> findByIdAndOwner(Long id, User owner);
}
