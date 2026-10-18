package com.shengid.rednote.web;

import static com.shengid.rednote.web.PageController.viewer;

import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Profile("preview")
public class ActionController {
  final RednoteApplicationPort app;

  public ActionController(RednoteApplicationPort app) {
    this.app = app;
  }

  record Like(@jakarta.validation.constraints.NotNull Boolean liked) {}

  record Comment(String text, Long target) {}

  record Status(String status) {}

  @PutMapping("/actions/notes/{id}/like")
  Object like(
      @PathVariable long id, @jakarta.validation.Valid @RequestBody Like f, Authentication a) {
    var n = app.like(viewer(a), id, f.liked());
    return Map.of("liked", n.liked(), "likes", n.likes());
  }

  @PostMapping("/actions/notes/{id}/comments")
  Object comment(@PathVariable long id, @RequestBody Comment f, Authentication a) {
    return app.comment(viewer(a), id, f.text(), f.target());
  }

  @DeleteMapping("/actions/comments/{id}")
  Object deleteComment(@PathVariable long id, Authentication a) {
    app.deleteComment(viewer(a), id);
    return Map.of("success", true);
  }

  @DeleteMapping("/actions/notes/{id}")
  Object delete(@PathVariable long id, Authentication a) {
    app.deleteNote(viewer(a), id);
    return Map.of("success", true);
  }

  @PostMapping("/actions/files")
  Object upload(@RequestParam MultipartFile file, Authentication a) {
    return app.upload(viewer(a), file);
  }

  @PutMapping("/actions/admin/users/{id}/status")
  Object status(@PathVariable long id, @RequestBody Status f, Authentication a) {
    return app.status(viewer(a), id, f.status());
  }

  @GetMapping("/preview/files/{key}")
  Object file(@PathVariable String key, Authentication a) {
    var file = app.file(key, viewer(a));
    return org.springframework.http.ResponseEntity.ok()
        .header("Cache-Control", "no-store")
        .header("X-Content-Type-Options", "nosniff")
        .contentType(org.springframework.http.MediaType.parseMediaType(file.mime()))
        .body(file.resource());
  }
}
