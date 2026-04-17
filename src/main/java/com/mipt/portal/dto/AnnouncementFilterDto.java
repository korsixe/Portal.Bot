package com.mipt.portal.dto;

import com.mipt.portal.enums.Category;
import com.mipt.portal.enums.Condition;
import java.time.Instant;
import lombok.Data;

@Data
public class AnnouncementFilterDto {
  private String text;
  private Integer minPrice;
  private Integer maxPrice;
  private Category category;
  private String subcategory;
  private Condition condition;
  private Instant createdAfter;
  private String sortBy;
  private String sortDirection;
}
