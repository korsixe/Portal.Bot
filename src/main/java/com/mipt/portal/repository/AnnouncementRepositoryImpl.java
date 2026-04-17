package com.mipt.portal.repository;

import com.mipt.portal.dto.AnnouncementFilterDto;
import com.mipt.portal.entity.Announcement;
import com.mipt.portal.enums.AdStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Transactional(readOnly = true)
public class AnnouncementRepositoryImpl implements CustomAnnouncementRepository {

  @PersistenceContext
  private EntityManager em;

  @Override
  public List<Announcement> searchApproved(AnnouncementFilterDto filter, String sortBy, String direction) {
    StringBuilder jpql = new StringBuilder("SELECT a FROM Announcement a WHERE a.status = :status");

    appendFilterConditions(jpql, filter);

    String sortField = (sortBy != null && !sortBy.isBlank()) ? sortBy : "createdAt";
    String sortDir = ("ASC".equalsIgnoreCase(direction)) ? "ASC" : "DESC";
    if (sortField.matches("^\\w+$")) {
      jpql.append(" ORDER BY a.").append(sortField).append(" ").append(sortDir);
    }

    TypedQuery<Announcement> query = em.createQuery(jpql.toString(), Announcement.class);
    query.setParameter("status", AdStatus.ACTIVE);

    setQueryParameters(query, filter);

    return query.getResultList();
  }

  private void appendFilterConditions(StringBuilder jpql, AnnouncementFilterDto filter) {
    if (filter.getText() != null && !filter.getText().isBlank()) {
      jpql.append(" AND (LOWER(a.title) LIKE LOWER(:text) OR LOWER(a.description) LIKE LOWER(:text))");
    }
    if (filter.getMinPrice() != null) {
      jpql.append(" AND a.price >= :minPrice");
    }
    if (filter.getMaxPrice() != null) {
      jpql.append(" AND a.price <= :maxPrice");
    }
    if (filter.getCategory() != null) {
      jpql.append(" AND a.category = :category");
    }
    if (filter.getSubcategory() != null && !filter.getSubcategory().isBlank()) {
      jpql.append(" AND a.subcategory = :subcategory");
    }
    if (filter.getCondition() != null) {
      jpql.append(" AND a.condition = :condition");
    }
    if (filter.getCreatedAfter() != null) {
      jpql.append(" AND a.createdAt >= :createdAfter");
    }
  }

  private void setQueryParameters(TypedQuery<Announcement> query, AnnouncementFilterDto filter) {
    if (filter.getText() != null && !filter.getText().isBlank()) {
      query.setParameter("text", "%" + filter.getText() + "%");
    }
    if (filter.getMinPrice() != null) {
      query.setParameter("minPrice", filter.getMinPrice());
    }
    if (filter.getMaxPrice() != null) {
      query.setParameter("maxPrice", filter.getMaxPrice());
    }
    if (filter.getCategory() != null) {
      query.setParameter("category", filter.getCategory());
    }
    if (filter.getSubcategory() != null && !filter.getSubcategory().isBlank()) {
      query.setParameter("subcategory", filter.getSubcategory());
    }
    if (filter.getCondition() != null) {
      query.setParameter("condition", filter.getCondition());
    }
    if (filter.getCreatedAfter() != null) {
      query.setParameter("createdAfter", filter.getCreatedAfter());
    }
  }
}
