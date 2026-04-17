package com.mipt.portal.entity;

import com.mipt.portal.enums.AdStatus;
import com.mipt.portal.enums.Category;
import com.mipt.portal.enums.Condition;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "ads")
public class Announcement {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String title;
  private String description;

  @Enumerated(EnumType.STRING)
  private Category category;

  private String subcategory;

  @Enumerated(EnumType.STRING)
  @Column(name = "condition")
  private Condition condition;

  private int price;
  private String location;

  @Column(name = "user_id")
  private Long authorId;

  @Enumerated(EnumType.STRING)
  private AdStatus status = AdStatus.DRAFT;

  @Column(name = "view_count")
  private Integer viewCount = 0;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private List<String> tags = new ArrayList<>();

  @Column(name = "tags_count")
  private Integer tagsCount = 0;

  @Column(name = "created_at")
  private java.time.Instant createdAt;

  @Column(name = "updated_at")
  private java.time.Instant updatedAt;

  // Время отправки уведомления об архивации. Null — уведомление не отправлялось.
  @Column(name = "notified_at")
  private java.time.Instant notifiedAt;

  @Column(columnDefinition = "bytea")
  private byte[] photo;

  public void sendToModeration() {
    this.status = AdStatus.UNDER_MODERATION;
  }

  public void activate() {
    if (this.status == AdStatus.UNDER_MODERATION) {
      this.status = AdStatus.ACTIVE;
    } else {
      throw new IllegalStateException("Можно активировать только из статуса модерации.");
    }
  }

  public void reject() {
    this.status = AdStatus.REJECTED;
  }

  public void setPhotoUrls(List<String> photoUrls) {
  }
}
