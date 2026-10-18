package com.shengid.rednote.web;

import static com.shengid.rednote.web.Views.*;

import java.util.*;
import org.springframework.web.multipart.MultipartFile;

/** Replace the preview adapter with transactional application services for production. */
public interface RednoteApplicationPort {
  User user(long id);

  Batch feed(String query, String cursor, Long viewer);

  Page<Note> profile(long id, int page, Long viewer);

  Note note(long id, Long viewer);

  CommentPage comments(long noteId, long root, int page, Long viewer);

  long commentCount(long noteId);

  void register(Forms.Registration form);

  long saveNote(Long viewer, Long id, Forms.NoteInput form);

  void profile(Long viewer, Forms.ProfileInput form);

  void password(Long viewer, Forms.PasswordInput form);

  Note like(Long viewer, long id, boolean liked);

  Comment comment(Long viewer, long id, String text, Long target);

  void deleteComment(Long viewer, long id);

  void deleteNote(Long viewer, long id);

  Upload upload(Long viewer, MultipartFile file);

  ImageFile file(String key, Long viewer);

  Page<User> users(String username, String name, String status, int page);

  User status(Long viewer, long id, String status);

  Dashboard dashboard();
}
