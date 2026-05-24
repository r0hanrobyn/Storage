package storage.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import storage.model.FileMetadata;
import storage.model.User;

import java.util.Optional;

@Repository
public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {

    Page<FileMetadata> findAllByOwner(User owner, Pageable pageable);

    Optional<FileMetadata> findByIdAndOwner(Long id, User owner);
}
