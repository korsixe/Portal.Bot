package com.mipt.portal.repository;

import com.mipt.portal.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    boolean existsByAnnouncementId(Long announcementId);

    List<Booking> findAllByBuyerId(Long buyerId);

    List<Booking> findByNotificationSentAtIsNull();

    Optional<Booking> findByAnnouncementId(Long announcementId);

    List<Booking> findAllByCreatedAtBefore(Instant timeLimit);

    List<Booking> findByCancelledAtIsNotNullAndCancelNotificationSentAtIsNull();

    List<Booking> findByConfirmedAtIsNotNullAndConfirmNotificationSentAtIsNull();

    void deleteByAnnouncementId(Long announcementId);
}
