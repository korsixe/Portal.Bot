package com.mipt.portal.entity;

import com.mipt.portal.enums.Role;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@Entity
@Table(name = "users")
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "email", nullable = false, unique = true)
  private String email;

  @Column(name = "hash_password", nullable = false)
  private String hashPassword;

  @Column(name = "salt", nullable = false)
  private String salt;

  @Column(name = "telegram_chat_id", unique = true)
  private Long telegramChatId;

  @Column(name = "name", nullable = false)
  private String name;

  @Embedded
  private Address address;

  @Column(name = "study_program")
  private String studyProgram;

  @Column(name = "course")
  private int course;

  @Column(name = "rating", columnDefinition = "FLOAT DEFAULT 3.0")
  private double rating = 3.0;

  @Column(name = "coins", columnDefinition = "INTEGER DEFAULT 0")
  private int coins = 0;

  @Column(name = "frozen_until")
  private Instant frozenUntil;

  @Column(name = "frozen_reason")
  private String frozenReason;

  @Column(name = "banned_until")
  private Instant bannedUntil;

  @Column(name = "ban_reason")
  private String banReason;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "user_ad_list", joinColumns = @JoinColumn(name = "user_id"))
  @Column(name = "ad_id")
  private List<Long> adList;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "role")
  private Set<Role> roles = new HashSet<>();

  public boolean hasRole(Role role) {
    return roles.contains(role);
  }

  public boolean isModerator() {
    return hasRole(Role.MODERATOR) || hasRole(Role.ADMIN);
  }

  public boolean isAdmin() {
    return hasRole(Role.ADMIN);
  }

  public void addRole(Role role) {
    this.roles.add(role);
  }

  public void removeRole(Role role) {
    this.roles.remove(role);
  }

  public void increaseRating(double increment) {
    if (this.rating + increment <= 5) {
      this.rating += increment;
    } else {
      this.rating = 5;
    }
  }

  public void decreaseRating(double decrement) {
    if (this.rating - decrement >= 1) {
      this.rating -= decrement;
    } else {
      this.rating = 1;
    }
  }

  public void addCoins(int amount) {
    this.coins += amount;
  }

  public boolean spendCoins(int amount) {
    if (this.coins - amount < 0) {
      return false;
    }
    this.coins -= amount;
    return true;
  }

  public boolean isFrozen() {
    return frozenUntil != null && Instant.now().isBefore(frozenUntil);
  }

  public boolean isBanned() {
    return bannedUntil != null && Instant.now().isBefore(bannedUntil);
  }
}
