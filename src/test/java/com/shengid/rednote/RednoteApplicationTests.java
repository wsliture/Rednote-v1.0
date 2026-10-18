package com.shengid.rednote;

import static org.junit.jupiter.api.Assertions.*;

import java.net.*;
import java.net.http.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("preview")
class RednoteApplicationTests {
  @LocalServerPort int port;

  HttpClient browser() {
    return HttpClient.newBuilder()
        .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
  }

  HttpResponse<String> get(HttpClient c, String path) throws Exception {
    return c.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }

  HttpResponse<String> send(
      HttpClient c, String method, String path, String body, String token, boolean json)
      throws Exception {
    var r =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header(
                "Content-Type", json ? "application/json" : "application/x-www-form-urlencoded");
    if (token != null) r.header("X-CSRF-TOKEN", token);
    return c.send(
        r.method(method, HttpRequest.BodyPublishers.ofString(body)).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  String token(String html) {
    var m = Pattern.compile("name=\"csrf-token\" content=\"([^\"]+)\"").matcher(html);
    assertTrue(m.find(), html.substring(0, Math.min(html.length(), 500)));
    return m.group(1);
  }

  String login(HttpClient c, String user) throws Exception {
    String t = token(get(c, "/login").body());
    var r = send(c, "POST", "/login", "username=" + user + "&password=Preview123", t, false);
    assertEquals(303, r.statusCode(), r.body());
    return token(get(c, "/").body());
  }

  @org.springframework.beans.factory.annotation.Autowired
  com.shengid.rednote.web.RednoteApplicationPort app;

  @Test
  void unicodeFilesOwnershipAndCommentCascade() throws Exception {
    var registration =
        new com.shengid.rednote.web.Forms.Registration(
            "boundary_user", "Boundary123", "Boundary123");
    app.register(registration);
    long id = app.users("boundary_user", "", "", 0).items().get(0).id();
    byte[] jpeg =
        java.nio.file.Files.readAllBytes(
            java.nio.file.Path.of("docs/prototype/images/photo-1441974231531-c6227db76b6e.jpg"));
    var upload =
        app.upload(
            id,
            new org.springframework.mock.web.MockMultipartFile(
                "file", "renamed.txt", "text/plain", jpeg));
    assertEquals(
        404,
        assertThrows(com.shengid.rednote.web.UiException.class, () -> app.file(upload.id(), 2L))
            .status);
    assertEquals(
        415,
        assertThrows(
                com.shengid.rednote.web.UiException.class,
                () ->
                    app.upload(
                        id,
                        new org.springframework.mock.web.MockMultipartFile(
                            "file", "fake.jpg", "image/jpeg", "not an image".getBytes())))
            .status);
    byte[] boundary = java.util.Arrays.copyOf(jpeg, 10 * 1024 * 1024);
    assertNotNull(
        app.upload(
            id,
            new org.springframework.mock.web.MockMultipartFile(
                "file", "boundary.jpg", "image/jpeg", boundary)));
    assertEquals(
        413,
        assertThrows(
                com.shengid.rednote.web.UiException.class,
                () ->
                    app.upload(
                        id,
                        new org.springframework.mock.web.MockMultipartFile(
                            "file", "too-big.jpg", "image/jpeg", new byte[10 * 1024 * 1024 + 1])))
            .status);
    var first = app.feed("", "", id);
    long publishedBefore =
        app.dashboard().trends().stream()
            .mapToLong(com.shengid.rednote.web.Views.Trend::notes)
            .sum();
    long note =
        app.saveNote(
            id,
            null,
            new com.shengid.rednote.web.Forms.NoteInput(
                "🌿".repeat(50), "正文", "", java.util.List.of(upload.id())));
    assertNotNull(app.file(upload.id(), null));
    assertFalse(app.feed("", first.next(), id).items().stream().anyMatch(n -> n.id() == note));
    assertEquals(
        400,
        assertThrows(
                com.shengid.rednote.web.UiException.class,
                () ->
                    app.saveNote(
                        id,
                        note,
                        new com.shengid.rednote.web.Forms.NoteInput(
                            "🌿".repeat(51), "正文", "1", java.util.List.of(upload.id()))))
            .status);
    assertEquals(
        409,
        assertThrows(
                com.shengid.rednote.web.UiException.class,
                () ->
                    app.saveNote(
                        id,
                        note,
                        new com.shengid.rednote.web.Forms.NoteInput(
                            "新版", "正文", "0", java.util.List.of(upload.id()))))
            .status);
    assertEquals(
        403,
        assertThrows(com.shengid.rednote.web.UiException.class, () -> app.deleteNote(2L, note))
            .status);
    var root = app.comment(2L, note, "主评论", null);
    var reply = app.comment(id, note, "回复", root.id());
    app.comment(3L, note, "回复具体回复", reply.id());
    assertEquals(3, app.commentCount(note));
    assertEquals(root.id(), app.comments(note, root.id(), 0, id).items().get(1).rootId());
    app.deleteComment(2L, root.id());
    assertEquals(0, app.commentCount(note));
    assertEquals(
        409,
        assertThrows(
                com.shengid.rednote.web.UiException.class,
                () -> app.comment(id, note, "目标已失效", root.id()))
            .status);
    var emptyPage = app.comments(note, 0, 0, id);
    assertTrue(emptyPage.items().isEmpty());
    assertFalse(emptyPage.hasMore());
    String emptyHtml = get(browser(), "/notes/" + note).body();
    assertEquals(1, emptyHtml.split("还没有评论，留下第一条想法吧。", -1).length - 1);
    var roots = new java.util.ArrayList<com.shengid.rednote.web.Views.Comment>();
    for (int i = 0; i < 20; i++) roots.add(app.comment(id, note, "分页评论 " + i, null));
    assertEquals(20, app.comments(note, 0, 0, id).items().size());
    assertFalse(app.comments(note, 0, 0, id).hasMore());
    roots.add(app.comment(id, note, "第 21 条主评论", null));
    assertTrue(app.comments(note, 0, 0, id).hasMore());
    assertEquals(1, app.comments(note, 0, 1, id).items().size());
    assertFalse(app.comments(note, 0, 1, id).hasMore());
    assertFalse(app.comments(note, roots.get(0).id(), 0, id).hasMore());
    for (int i = 0; i < 20; i++) app.comment(id, note, "分页回复 " + i, roots.get(0).id());
    assertFalse(app.comments(note, roots.get(0).id(), 0, id).hasMore());
    app.comment(id, note, "第 21 条回复", roots.get(0).id());
    assertTrue(app.comments(note, roots.get(0).id(), 0, id).hasMore());
    assertEquals(1, app.comments(note, roots.get(0).id(), 1, id).items().size());
    assertFalse(app.comments(note, roots.get(0).id(), 1, id).hasMore());
    String endHtml = get(browser(), "/fragments/comments/" + note + "?page=2").body();
    assertFalse(endHtml.contains("还没有评论"));
    assertFalse(endHtml.contains("暂无更多评论"));
    assertTrue(endHtml.contains("data-has-more=\"false\""));
    app.deleteNote(id, note);
    assertEquals(
        publishedBefore + 1,
        app.dashboard().trends().stream()
            .mapToLong(com.shengid.rednote.web.Views.Trend::notes)
            .sum());
    assertEquals(
        404,
        assertThrows(com.shengid.rednote.web.UiException.class, () -> app.file(upload.id(), null))
            .status);
  }

  @Test
  void loginReturnAndPasswordRevokesOtherSessions() throws Exception {
    app.register(
        new com.shengid.rednote.web.Forms.Registration("session_user", "Preview123", "Preview123"));
    var first = browser();
    var second = browser();
    String t = login(first, "session_user"), secondToken = login(second, "session_user");
    var changed =
        send(
            first,
            "POST",
            "/settings/password",
            "old=Preview123&password=Changed123&confirm=Changed123",
            t,
            false);
    assertEquals(303, changed.statusCode());
    assertTrue(changed.headers().firstValue("Location").orElseThrow().contains("passwordChanged"));
    assertEquals(
        401,
        send(second, "PUT", "/actions/notes/1/like", "{\"liked\":true}", secondToken, true)
            .statusCode());
    var attempt = browser();
    String token = token(get(attempt, "/login").body());
    var failed =
        send(
            attempt,
            "POST",
            "/login",
            "username=session_user&password=Preview123&returnTo=%2Fnotes%2Fnew",
            token,
            false);
    assertEquals(302, failed.statusCode());
    assertTrue(
        failed.headers().firstValue("Location").orElseThrow().contains("returnTo=%2Fnotes%2Fnew"));
    var safe =
        send(
            attempt,
            "POST",
            "/login",
            "username=session_user&password=Changed123&returnTo=%2F%2Fevil.example",
            token(get(attempt, "/login").body()),
            false);
    assertEquals(303, safe.statusCode());
    assertEquals("/", safe.headers().firstValue("Location").orElseThrow());
    var disabled = browser();
    var result =
        send(
            disabled,
            "POST",
            "/login",
            "username=xiaoyu&password=Preview123",
            token(get(disabled, "/login").body()),
            false);
    assertTrue(result.headers().firstValue("Location").orElseThrow().contains("disabled"));
  }

  @Test
  void templatesPermissionsAndInteractions() throws Exception {
    var guest = browser();
    for (String path :
        new String[] {
          "/",
          "/search?q=山",
          "/notes/1",
          "/users/1",
          "/login",
          "/register",
          "/fragments/feed",
          "/fragments/comments/1"
        }) {
      var r = get(guest, path);
      assertEquals(200, r.statusCode(), path + " " + r.body());
      assertFalse(r.body().contains("th:text"), path);
    }
    String home = get(guest, "/").body();
    assertEquals(20, home.split("data-note-id=").length - 1);
    assertFalse(home.contains("#/"));
    var next = Pattern.compile("data-next=\"([^\"]+)\"").matcher(home);
    assertTrue(next.find());
    String second = get(guest, "/fragments/feed?cursor=" + next.group(1)).body();
    assertEquals(20, second.split("data-note-id=").length - 1);
    assertFalse(second.contains("data-note-id=\"1\""));
    assertEquals(302, get(guest, "/notes/new").statusCode());
    assertEquals(302, get(guest, "/admin").statusCode());
    assertEquals(
        401,
        send(guest, "PUT", "/actions/notes/1/like", "{\"liked\":true}", token(home), true)
            .statusCode());
    var user = browser();
    String t = login(user, "linxia");
    for (String path : new String[] {"/notes/new", "/notes/1/edit", "/settings"})
      assertEquals(200, get(user, path).statusCode(), path);
    assertEquals(403, get(user, "/admin").statusCode());
    assertEquals(403, get(user, "/notes/2/edit").statusCode());
    assertEquals(
        403,
        send(user, "PUT", "/actions/notes/1/like", "{\"liked\":true}", null, true).statusCode());
    String first = send(user, "PUT", "/actions/notes/1/like", "{\"liked\":true}", t, true).body();
    assertEquals(
        first, send(user, "PUT", "/actions/notes/1/like", "{\"liked\":true}", t, true).body());
    assertEquals(
        200,
        send(
                user,
                "POST",
                "/actions/notes/1/comments",
                "{\"text\":\"<script>alert(1)</script>\"}",
                t,
                true)
            .statusCode());
    String detail = get(user, "/notes/1").body();
    assertTrue(detail.contains("&lt;script&gt;"));
    assertFalse(detail.contains("<script>alert(1)</script>"));
    var invalid = send(user, "POST", "/notes", "title=&body=test", t, false);
    assertEquals(200, invalid.statusCode());
    assertTrue(invalid.body().contains("请输入"), invalid.body());
    var registration =
        send(
            guest,
            "POST",
            "/register",
            "username=a&password=secret&confirm=secret",
            token(get(guest, "/register").body()),
            false);
    assertEquals(200, registration.statusCode());
    assertFalse(registration.body().contains("value=\"secret\""));
    var admin = browser();
    String at = login(admin, "admin");
    for (String path :
        new String[] {
          "/admin", "/admin/users", "/fragments/admin/users/1", "/fragments/admin/dashboard"
        }) {
      var r = get(admin, path);
      assertEquals(200, r.statusCode(), path + r.body());
      assertFalse(r.body().contains("Preview123"));
      assertFalse(r.body().contains("password"));
    }
    assertEquals(
        403,
        send(admin, "PUT", "/actions/admin/users/4/status", "{\"status\":\"禁用\"}", at, true)
            .statusCode());
    assertEquals(
        200,
        send(admin, "PUT", "/actions/admin/users/1/status", "{\"status\":\"禁用\"}", at, true)
            .statusCode());
    assertEquals(
        401, send(user, "PUT", "/actions/notes/1/like", "{\"liked\":false}", t, true).statusCode());
    send(admin, "PUT", "/actions/admin/users/1/status", "{\"status\":\"正常\"}", at, true);
    assertEquals(404, get(guest, "/notes/999999").statusCode());
  }
}
