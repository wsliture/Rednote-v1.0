package com.shengid.rednote.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "file_asset")
public class FileAsset {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public Long uploaderId;

  @Column(length = 128)
  public String storageKey;

  @Column(length = 32)
  public String mimeType;

  public long sizeBytes;
  public int width;
  public int height;

  @Column(length = 16)
  public String state;

  @Column(columnDefinition = "DATETIME(6)")
  public Instant createdAt;

  @Column(columnDefinition = "DATETIME(6)")
  public Instant readyAt;

  @Column(columnDefinition = "DATETIME(6)")
  public Instant unreferencedSince;

  @Column(columnDefinition = "DATETIME(6)")
  public Instant updatedAt;

  @Column(columnDefinition = "DATETIME(6)")
  public Instant deletedAt;
}
