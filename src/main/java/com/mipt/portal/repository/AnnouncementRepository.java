package com.mipt.portal.repository;

import com.mipt.portal.entity.Announcement;
import com.mipt.portal.enums.AdStatus;
import com.mipt.portal.enums.Category;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Long>, CustomAnnouncementRepository {
    List<Announcement> findAllByAuthorId(Long authorId);

    List<Announcement> findAllByCategoryAndStatus(Category category, AdStatus status);

    long countByCategoryAndStatus(Category category, AdStatus status);

    void deleteAllByAuthorIdAndStatus(Long authorId, AdStatus status);

    List<Announcement> findAllByStatus(AdStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Announcement a WHERE a.id = :id")
    Optional<Announcement> findByIdWithLock(@Param("id") Long id);

    List<Announcement> findByAuthorId(Long authorId);

    List<Announcement> findByStatusAndUpdatedAtBefore(AdStatus status, Instant date);

    // Объявления, уведомление по которым было отправлено до указанной даты (не подтверждены)
    List<Announcement> findByStatusAndNotifiedAtBefore(AdStatus status, Instant date);
}
