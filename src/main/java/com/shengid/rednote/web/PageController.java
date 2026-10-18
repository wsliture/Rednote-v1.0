package com.shengid.rednote.web;

import static com.shengid.rednote.web.Views.*;

import com.shengid.rednote.security.SessionUser;
import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@Profile("preview")
public class PageController {
  final RednoteApplicationPort app;

  public PageController(RednoteApplicationPort app) {
    this.app = app;
  }

  static Long viewer(Authentication a) {
    return a != null && a.getPrincipal() instanceof SessionUser p ? p.id() : null;
  }

  @ModelAttribute
  void common(Model m, Authentication a, HttpServletRequest request) {
    Long id = viewer(a);
    m.addAttribute("me", id == null ? null : app.user(id));
    m.addAttribute("q", "");
    m.addAttribute("form", Map.of());
    m.addAttribute("fieldErrors", Map.of());
    m.addAttribute("message", "");
    m.addAttribute("path", request.getRequestURI().substring(request.getContextPath().length()));
  }

  @GetMapping({"/", "/search"})
  String feed(@RequestParam(defaultValue = "") String q, Model m, Authentication a) {
    m.addAttribute("q", q.strip());
    m.addAttribute("batch", app.feed(q, "", viewer(a)));
    return "feed";
  }

  @GetMapping("/notes/{id}")
  String note(@PathVariable long id, Model m, Authentication a) {
    m.addAttribute("note", app.note(id, viewer(a)));
    var comments = app.comments(id, 0, 0, viewer(a));
    m.addAttribute("commentPage", comments);
    m.addAttribute("commentCount", comments.total());
    m.addAttribute("page", 0);
    m.addAttribute("root", 0);
    return "note/detail";
  }

  @GetMapping({"/notes/new", "/notes/{id}/edit"})
  String editor(@PathVariable(required = false) Long id, Model m, Authentication a) {
    Note n = id == null ? null : app.note(id, viewer(a));
    if (n != null && !n.owner()) throw new UiException(403, "只能编辑自己的笔记");
    m.addAttribute("note", n);
    m.addAttribute(
        "form",
        n == null
            ? Map.of()
            : Map.of("title", n.title(), "body", n.body(), "version", String.valueOf(n.version())));
    m.addAttribute("imageIds", n == null ? List.of() : n.imageIds());
    m.addAttribute("imageUrls", n == null ? List.of() : n.images());
    return "note/editor";
  }

  @GetMapping({"/login", "/register"})
  String auth(HttpServletRequest r) {
    return r.getRequestURI().endsWith("/login") ? "auth/login" : "auth/register";
  }

  @GetMapping("/users/{id}")
  String profile(
      @PathVariable long id,
      @RequestParam(defaultValue = "0") int page,
      Model m,
      Authentication a) {
    m.addAttribute("profile", app.user(id));
    m.addAttribute("pagination", app.profile(id, page, viewer(a)));
    return "user/profile";
  }

  @GetMapping("/settings")
  String settings(Model m, Authentication a) {
    var u = app.user(viewer(a));
    m.addAttribute("form", Map.of("name", u.name(), "bio", u.bio(), "avatar", u.avatar()));
    return "user/settings";
  }

  @GetMapping("/admin")
  String dashboard(Model m) {
    m.addAttribute("dashboard", app.dashboard());
    return "admin/dashboard";
  }

  @GetMapping("/admin/users")
  String users(
      @RequestParam(defaultValue = "") String username,
      @RequestParam(defaultValue = "") String name,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "0") int page,
      Model m) {
    m.addAttribute("filters", Map.of("username", username, "name", name, "status", status));
    m.addAttribute("pagination", app.users(username, name, status, page));
    return "admin/users";
  }

  ResponseEntity<Void> redirect(String path, HttpServletRequest r) {
    return ResponseEntity.status(303)
        .location(java.net.URI.create(r.getContextPath() + path))
        .build();
  }

  @PostMapping("/register")
  Object register(@RequestParam Map<String, String> f, Model m, HttpServletRequest r) {
    try {
      app.register(Forms.Registration.from(f));
      return redirect("/login?registered", r);
    } catch (UiException e) {
      if (enhanced(r)) return invalid(e);
      failure(m, f, e);
      return "auth/register";
    }
  }

  @PostMapping({"/notes", "/notes/{id}"})
  Object save(
      @PathVariable(required = false) Long id,
      @RequestParam Map<String, String> f,
      @RequestParam(defaultValue = "") List<String> images,
      Model m,
      Authentication a,
      HttpServletRequest r) {
    try {
      return redirect("/notes/" + app.saveNote(viewer(a), id, Forms.NoteInput.from(f, images)), r);
    } catch (UiException e) {
      if (enhanced(r)) return invalid(e);
      failure(m, f, e);
      Note n = id == null ? null : app.note(id, viewer(a));
      m.addAttribute("note", n);
      m.addAttribute("imageIds", images);
      m.addAttribute(
          "imageUrls",
          images.stream().map(x -> x.startsWith("/") ? x : "/preview/files/" + x).toList());
      return "note/editor";
    }
  }

  @PostMapping("/settings/profile")
  Object profile(
      @RequestParam Map<String, String> f, Model m, Authentication a, HttpServletRequest r) {
    try {
      app.profile(viewer(a), Forms.ProfileInput.from(f));
      return redirect("/users/" + viewer(a), r);
    } catch (UiException e) {
      if (enhanced(r)) return invalid(e);
      failure(m, f, e);
      return "user/settings";
    }
  }

  @PostMapping("/settings/password")
  Object password(
      @RequestParam Map<String, String> f, Model m, Authentication a, HttpServletRequest r) {
    try {
      app.password(viewer(a), Forms.PasswordInput.from(f));
      r.getSession().invalidate();
      org.springframework.security.core.context.SecurityContextHolder.clearContext();
      return redirect("/login?passwordChanged", r);
    } catch (UiException e) {
      if (enhanced(r)) return invalid(e);
      var u = app.user(viewer(a));
      failure(m, Map.of("name", u.name(), "bio", u.bio(), "avatar", u.avatar()), e);
      m.addAttribute("passwordTab", true);
      return "user/settings";
    }
  }

  boolean enhanced(HttpServletRequest r) {
    return "1".equals(r.getHeader("X-Rednote-Form"));
  }

  ResponseEntity<Failure> invalid(UiException e) {
    return ResponseEntity.status(e.status)
        .body(new Failure("UI_" + e.status, e.getMessage(), e.fields));
  }

  void failure(Model m, Map<String, String> f, UiException e) {
    Map<String, String> safe = new HashMap<>(f);
    List.of("password", "confirm", "old", "_csrf").forEach(safe::remove);
    m.addAttribute("form", safe);
    m.addAttribute("fieldErrors", e.fields);
    m.addAttribute("message", e.getMessage());
  }

  @GetMapping("/fragments/feed")
  String feedFragment(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "") String cursor,
      Model m,
      Authentication a) {
    m.addAttribute("batch", app.feed(q, cursor, viewer(a)));
    return "fragments/components :: batch";
  }

  @GetMapping("/fragments/comments/{id}")
  String comments(
      @PathVariable long id,
      @RequestParam(defaultValue = "0") long root,
      @RequestParam(defaultValue = "0") int page,
      Model m,
      Authentication a) {
    m.addAttribute("commentPage", app.comments(id, root, page, viewer(a)));
    m.addAttribute("noteId", id);
    m.addAttribute("root", root);
    m.addAttribute("page", page);
    return "fragments/components :: comments";
  }

  @GetMapping("/fragments/admin/dashboard")
  String dashboardFragment(Model m) {
    m.addAttribute("dashboard", app.dashboard());
    return "admin/dashboard :: overview";
  }

  @GetMapping("/fragments/admin/users/{id}")
  String detail(@PathVariable long id, Model m) {
    m.addAttribute("user", app.user(id));
    return "fragments/components :: userDetail";
  }
}
