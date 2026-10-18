package com.shengid.rednote.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "app_user")
public class AppUser {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(length = 20)
  public String username;

  @Column(length = 20)
  public String usernameNormalized;

  @Column(length = 255)
  public String passwordHash;

  @Column(length = 20)
  public String nickname;

  @Column(length = 200)
  public String bio;

  public Long avatarFileId;

  @Column(length = 16)
  public String role;

  @Column(length = 16)
  public String status;

  public long authVersion;

  @Column(columnDefinition = "DATETIME(6)")
  public Instant createdAt;

  @Column(columnDefinition = "DATETIME(6)")
  public Instant updatedAt;
}
