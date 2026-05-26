package storage.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import storage.model.FileMetadata;
import storage.model.User;

import java.util.Optional;

@Repository
public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {

    Page<FileMetadata> findAllByOwner(User owner, Pageable pageable);

    Page<FileMetadata> findAllByOwnerAndOriginalFilenameContainingIgnoreCase(
            User owner, String name, Pageable pageable);

    Optional<FileMetadata> findByIdAndOwner(Long id, User owner);

    @Query("SELECT COALESCE(SUM(f.size), 0) FROM FileMetadata f WHERE f.owner = :owner")
    long sumSizeByOwner(@Param("owner") User owner);
}
