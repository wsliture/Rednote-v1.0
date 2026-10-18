package com.shengid.rednote.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "note")
public class NoteEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public Long authorId;

  @Column(length = 50)
  public String title;

  @Column(columnDefinition = "TEXT")
  public String body;

  @Column(columnDefinition = "DATETIME(6)")
  public Instant publishedAt;

  @Column(columnDefinition = "DATETIME(6)")
  public Instant updatedAt;

  @Column(columnDefinition = "DATETIME(6)")
  public Instant deletedAt;

  @Version public Long version;
}
