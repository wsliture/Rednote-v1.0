package com.shengid.rednote.service;

import static com.shengid.rednote.web.Views.*;

import com.shengid.rednote.web.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Thin application facade; transaction boundaries belong to individual use-case services. */
@Service
public class DatabaseApplication implements RednoteApplicationPort {
  private final ReadService read;
  private final UserService users;
  private final NoteService notes;
  private final InteractionService interactions;
  private final AdminService admin;
  private final FileService files;

  public DatabaseApplication(
      ReadService read,
      UserService users,
      NoteService notes,
      InteractionService interactions,
      AdminService admin,
      FileService files) {
    this.read = read;
    this.users = users;
    this.notes = notes;
    this.interactions = interactions;
    this.admin = admin;
    this.files = files;
  }

  public PublicUser publicUser(long id) {
    return read.publicUser(id);
  }

  public User user(long id) {
    return read.user(id);
  }

  public Batch feed(String q, String cursor, Long viewer) {
    return read.feed(q, cursor, viewer);
  }

  public Page<Note> profile(long id, int page, Long viewer) {
    return read.profile(id, page, viewer);
  }

  public Note note(long id, Long viewer) {
    return read.note(id, viewer);
  }

  public CommentPage comments(long note, long root, int page, Long viewer) {
    return read.comments(note, root, page, viewer);
  }

  public long commentCount(long id) {
    return read.commentCount(id);
  }

  public void register(Forms.Registration f) {
    users.register(f);
  }

  public long saveNote(Long viewer, Long id, Forms.NoteInput f) {
    return notes.save(viewer, id, f);
  }

  public void profile(Long viewer, Forms.ProfileInput f) {
    users.profile(viewer, f);
  }

  public void password(Long viewer, Forms.PasswordInput f) {
    users.password(viewer, f);
  }

  public Note like(Long viewer, long id, boolean liked) {
    return interactions.like(viewer, id, liked);
  }

  public Comment comment(Long viewer, long id, String text, Long target) {
    return interactions.comment(viewer, id, text, target);
  }

  public void deleteComment(Long viewer, long id) {
    interactions.delete(viewer, id);
  }

  public void deleteNote(Long viewer, long id) {
    notes.delete(viewer, id);
  }

  public Upload upload(Long viewer, MultipartFile file) {
    return files.upload(viewer, file);
  }

  public ImageFile file(String id, Long viewer) {
    return files.read(id, viewer);
  }

  public void deleteFile(Long viewer, long id) {
    files.delete(viewer, id);
  }

  public Page<User> users(String username, String name, String status, int page) {
    return admin.users(username, name, status, page);
  }

  public User status(Long viewer, long id, String status) {
    return admin.status(viewer, id, status);
  }

  public Dashboard dashboard() {
    return admin.dashboard();
  }
}
