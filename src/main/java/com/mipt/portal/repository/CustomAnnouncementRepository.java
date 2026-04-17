package com.mipt.portal.repository;

import com.mipt.portal.dto.AnnouncementFilterDto;
import com.mipt.portal.entity.Announcement;

import java.util.List;

public interface CustomAnnouncementRepository {
  List<Announcement> searchApproved(AnnouncementFilterDto filter, String sortBy, String direction);
}
