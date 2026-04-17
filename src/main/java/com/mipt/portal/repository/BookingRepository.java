package com.mipt.portal.repository;

import com.mipt.portal.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    boolean existsByAnnouncementId(Long announcementId);

    List<Booking> findAllByBuyerId(Long buyerId);

    List<Booking> findByNotificationSentAtIsNull();
}
