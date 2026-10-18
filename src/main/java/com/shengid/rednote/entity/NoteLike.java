package com.shengid.rednote.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "note_like")
public class NoteLike {
  @Embeddable
  public static class Key implements Serializable {
    public Long userId;
    public Long noteId;

    public Key() {}

    public Key(long user, long note) {
      userId = user;
      noteId = note;
    }

    public boolean equals(Object o) {
      return o instanceof Key k
          && Objects.equals(userId, k.userId)
          && Objects.equals(noteId, k.noteId);
    }

    public int hashCode() {
      return Objects.hash(userId, noteId);
    }
  }

  @EmbeddedId public Key id;

  @Column(columnDefinition = "DATETIME(6)")
  public Instant createdAt;
}
