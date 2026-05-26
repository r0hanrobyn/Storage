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

    // ── Active files ──────────────────────────────────────────────
    Page<FileMetadata> findAllByOwnerAndDeletedFalse(User owner, Pageable pageable);

    Page<FileMetadata> findAllByOwnerAndDeletedFalseAndOriginalFilenameContainingIgnoreCase(
            User owner, String name, Pageable pageable);

    Page<FileMetadata> findAllByOwnerAndDeletedFalseAndStarredTrue(User owner, Pageable pageable);

    Page<FileMetadata> findAllByOwnerAndDeletedFalseAndStarredTrueAndOriginalFilenameContainingIgnoreCase(
            User owner, String name, Pageable pageable);

    // ── Bin (soft-deleted) ────────────────────────────────────────
    Page<FileMetadata> findAllByOwnerAndDeletedTrue(User owner, Pageable pageable);

    // ── By id + owner (active only) ───────────────────────────────
    Optional<FileMetadata> findByIdAndOwnerAndDeletedFalse(Long id, User owner);

    // ── Bin item ──────────────────────────────────────────────────
    Optional<FileMetadata> findByIdAndOwnerAndDeletedTrue(Long id, User owner);

    // ── Quota: only count active (non-deleted) files ──────────────
    @Query("SELECT COALESCE(SUM(f.size), 0) FROM FileMetadata f WHERE f.owner = :owner AND f.deleted = false")
    long sumSizeByOwner(@Param("owner") User owner);
}
