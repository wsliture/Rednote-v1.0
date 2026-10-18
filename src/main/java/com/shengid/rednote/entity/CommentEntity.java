package com.shengid.rednote.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "`comment`")
public class CommentEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public Long noteId;
  public Long authorId;
  public Long rootCommentId;
  public Long targetCommentId;
  public Long replyToUserId;

  @Column(length = 500)
  public String content;

  @Column(columnDefinition = "DATETIME(6)")
  public Instant createdAt;

  @Column(columnDefinition = "DATETIME(6)")
  public Instant deletedAt;
}
