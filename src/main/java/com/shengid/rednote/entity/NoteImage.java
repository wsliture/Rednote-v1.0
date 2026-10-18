package com.shengid.rednote.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "note_image")
public class NoteImage {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public Long noteId;
  public Long fileId;
  public byte sortOrder;
}
