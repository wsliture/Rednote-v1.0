package com.shengid.rednote;

import static org.junit.jupiter.api.Assertions.*;

import com.shengid.rednote.entity.*;
import com.shengid.rednote.repository.*;
import com.shengid.rednote.security.SessionUser;
import com.shengid.rednote.service.*;
import com.shengid.rednote.web.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RednoteApplicationTests {
  @LocalServerPort int port;
  @Autowired RednoteApplicationPort app;
  @Autowired UserService users;
  @Autowired IdentityService identities;
  @Autowired AppUserRepository userRepo;
  @Autowired FileAssetRepository files;
  @Autowired FileService fileService;
  @Autowired FileTransactions fileTx;
  @Autowired PlatformTransactionManager manager;
  @Autowired Queries queries;
  static final String PASSWORD = "Testing12345";
  static final String suffix = UUID.randomUUID().toString().substring(0, 8);
  static final String A = "testa_" + suffix, B = "testb_" + suffix, M = "testm_" + suffix;
  static long a, b, m;
  static Path directory;

  @DynamicPropertySource
  static void storage(DynamicPropertyRegistry registry) throws IOException {
    directory = Files.createTempDirectory("rednote-integration-");
    registry.add("rednote.storage.root", () -> directory.toString());
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  void as(long id) {
    var u = userRepo.findById(id).orElseThrow();
    var p =
        new SessionUser(
            u.id,
            u.authVersion,
            u.username,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_" + u.role)),
            true);
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(p, null, p.getAuthorities()));
  }

  long account(String name) {
    return identities.account(name).id;
  }

  byte[] png() throws IOException {
    var out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", out);
    return out.toByteArray();
  }

  Views.Upload upload(long id) throws IOException {
    as(id);
    return app.upload(id, new MockMultipartFile("file", "file.png", "image/png", png()));
  }

  long note(long user, String title, String file) {
    as(user);
    return app.saveNote(
        user, null, new Forms.NoteInput(title, "正文\n<script>safe</script>", "", List.of(file)));
  }

  void status(int status, Runnable task) {
    assertEquals(status, assertThrows(UiException.class, task::run).status);
  }

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
    var matcher = Pattern.compile("name=\"csrf-token\" content=\"([^\"]+)\"").matcher(html);
    assertTrue(matcher.find(), html.substring(0, Math.min(html.length(), 500)));
    return matcher.group(1);
  }

  String login(HttpClient c, String name) throws Exception {
    var t = token(get(c, "/login").body());
    var r = send(c, "POST", "/login", "username=" + name + "&password=" + PASSWORD, t, false);
    assertEquals(303, r.statusCode(), r.body());
    return token(get(c, "/").body());
  }

  @Test
  @Order(1)
  void migrationRegistrationUnicodeAndPassword() {
    app.register(new Forms.Registration(A, PASSWORD, PASSWORD));
    app.register(new Forms.Registration(B, PASSWORD, PASSWORD));
    users.initializeAdmin(M, PASSWORD);
    a = account(A);
    b = account(B);
    m = account(M);
    status(
        409,
        () -> app.register(new Forms.Registration(A.toUpperCase(Locale.ROOT), PASSWORD, PASSWORD)));
    status(400, () -> app.register(new Forms.Registration("bad", "nonsense", "nonsense")));
    as(a);
    app.profile(a, new Forms.ProfileInput("默认头像", "", app.user(a).avatar()));
    assertEquals("/images/avatar-default.svg", app.user(a).avatar());
    app.profile(a, new Forms.ProfileInput("😀".repeat(20), "", ""));
    assertEquals("😀".repeat(20), app.user(a).name());
    status(400, () -> app.profile(a, new Forms.ProfileInput("😀".repeat(21), "", "")));
    var hash = userRepo.findById(a).orElseThrow().passwordHash;
    assertTrue(hash.startsWith("{pbkdf2-rednote-v1}"));
    assertFalse(hash.contains(PASSWORD));
  }

  @Test
  @Order(2)
  void imagesOwnershipVersionsReferencesAndCleanup() throws Exception {
    var file = upload(a);
    String fid = file.id();
    long n = note(a, "图片与版本", fid);
    var original = app.note(n, a);
    as(b);
    status(403, () -> app.saveNote(b, null, new Forms.NoteInput("非法引用", "正文", "", List.of(fid))));
    status(403, () -> app.deleteNote(b, n));
    try (var stream = app.file(fid, null).resource().getInputStream()) {
      assertTrue(stream.readAllBytes().length > 0);
    }
  }

  @Test
  @Order(3)
  void noteEditingAndSharedFileLifecycle() throws Exception {
    var one = upload(a);
    var two = upload(a);
    long n = note(a, "旧标题", one.id());
    var before = app.note(n, a);
    as(a);
    long n2 = note(a, "共享图片", one.id());
    app.profile(a, new Forms.ProfileInput("测试作者", "简介", one.id()));
    app.saveNote(
        a,
        n,
        new Forms.NoteInput(
            "新标题", "新正文", Long.toString(before.version()), List.of(two.id(), one.id())));
    var edited = app.note(n, a);
    assertEquals(before.date(), edited.date());
    assertEquals(List.of(two.id(), one.id()), edited.imageIds());
    status(
        409,
        () ->
            app.saveNote(
                a,
                n,
                new Forms.NoteInput(
                    "冲突", "正文", Long.toString(before.version()), List.of(one.id()))));
    status(409, () -> app.deleteFile(a, Long.parseLong(one.id())));
    app.deleteNote(a, n);
    app.deleteNote(a, n2);
    status(409, () -> app.deleteFile(a, Long.parseLong(one.id())));
    app.profile(a, new Forms.ProfileInput("测试作者", "", ""));
    app.deleteFile(a, Long.parseLong(one.id()));
    assertEquals("DELETED", files.findById(Long.parseLong(one.id())).orElseThrow().state);
    var orphan = upload(a);
    new TransactionTemplate(manager)
        .executeWithoutResult(
            tx -> {
              var f = files.lock(Long.parseLong(orphan.id())).orElseThrow();
              f.unreferencedSince = Instant.now().minus(Duration.ofHours(25));
            });
    fileService.cleanup();
    assertEquals("DELETED", files.findById(Long.parseLong(orphan.id())).orElseThrow().state);
  }

  @Test
  @Order(4)
  void commentsPaginationTargetsAndCascade() throws Exception {
    long n = note(a, "评论测试", upload(a).id());
    as(a);
    var root = app.comment(a, n, "根评论", null);
    as(b);
    var reply = app.comment(b, n, "第一回复", root.id());
    as(a);
    var next = app.comment(a, n, "回复具体回复", reply.id());
    assertEquals(root.id(), next.rootId());
    status(403, () -> app.deleteComment(a, reply.id()));
    as(b);
    app.deleteComment(b, reply.id());
    assertEquals(2, app.commentCount(n));
    assertEquals(app.user(b).name(), app.comments(n, root.id(), 0, a).items().get(0).replyName());
    status(409, () -> app.comment(b, n, "失效目标", reply.id()));
    as(a);
    app.deleteComment(a, root.id());
    assertEquals(0, app.commentCount(n));
    for (int i = 0; i < 21; i++) app.comment(a, n, "评论" + i, null);
    assertEquals(20, app.comments(n, 0, 0, a).items().size());
    assertTrue(app.comments(n, 0, 0, a).hasMore());
    assertEquals(1, app.comments(n, 0, 1, a).items().size());
    app.deleteNote(a, n);
    status(404, () -> app.comment(a, n, "已删笔记", null));
  }

  @Test
  @Order(5)
  void concurrentLikesAndDeleteCompetition() throws Exception {
    long n = note(a, "并发测试", upload(a).id());
    ExecutorService pool = Executors.newFixedThreadPool(8);
    try {
      var jobs = new ArrayList<Callable<Void>>();
      for (int i = 0; i < 16; i++)
        jobs.add(
            () -> {
              try {
                as(b);
                app.like(b, n, true);
                return null;
              } finally {
                SecurityContextHolder.clearContext();
              }
            });
      for (var f : pool.invokeAll(jobs)) f.get();
      assertEquals(1, app.note(n, a).likes());
      var gate = new CountDownLatch(1);
      var d =
          pool.submit(
              () -> {
                as(a);
                try {
                  gate.await();
                  app.deleteNote(a, n);
                } catch (InterruptedException e) {
                  throw new RuntimeException(e);
                } finally {
                  SecurityContextHolder.clearContext();
                }
              });
      var r =
          pool.submit(
              () -> {
                as(b);
                try {
                  gate.await();
                  app.comment(b, n, "竞争评论", null);
                } catch (UiException e) {
                  assertEquals(404, e.status);
                } catch (InterruptedException e) {
                  throw new RuntimeException(e);
                } finally {
                  SecurityContextHolder.clearContext();
                }
              });
      gate.countDown();
      d.get();
      r.get();
      status(404, () -> app.note(n, a));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  @Order(6)
  void realHttpTemplatesSecuritySessionsAndPassword() throws Exception {
    long n = note(a, "HTTP安全验证", upload(a).id());
    var guest = browser();
    for (String p :
        List.of(
            "/",
            "/login",
            "/register",
            "/notes/" + n,
            "/users/" + a,
            "/fragments/feed",
            "/fragments/comments/" + n)) assertEquals(200, get(guest, p).statusCode(), p);
    assertEquals(302, get(guest, "/admin").statusCode());
    assertEquals(
        401,
        send(
                guest,
                "PUT",
                "/actions/notes/" + n + "/like",
                "{\"liked\":true}",
                token(get(guest, "/").body()),
                true)
            .statusCode());
    var first = browser();
    var second = browser();
    String t = login(first, A);
    login(second, A);
    assertEquals(
        403,
        send(first, "PUT", "/actions/notes/" + n + "/like", "{\"liked\":true}", null, true)
            .statusCode());
    assertEquals(403, get(first, "/admin").statusCode());
    String body = get(first, "/notes/" + n).body();
    assertTrue(body.contains("&lt;script&gt;"));
    assertFalse(body.contains("<script>safe</script>"));
    var admin = browser();
    String at = login(admin, M);
    for (String p :
        List.of(
            "/admin", "/admin/users", "/fragments/admin/users/" + a, "/fragments/admin/dashboard"))
      assertEquals(200, get(admin, p).statusCode(), p);
    assertEquals(
        200,
        send(admin, "PUT", "/actions/admin/users/" + a + "/status", "{\"status\":\"禁用\"}", at, true)
            .statusCode());
    assertEquals(
        401,
        send(first, "PUT", "/actions/notes/" + n + "/like", "{\"liked\":true}", t, true)
            .statusCode());
    assertEquals(302, get(second, "/settings").statusCode());
    assertEquals(200, get(guest, "/notes/" + n).statusCode());
    send(admin, "PUT", "/actions/admin/users/" + a + "/status", "{\"status\":\"正常\"}", at, true);
    assertEquals(302, get(second, "/settings").statusCode());
    t = login(first, A);
    login(second, A);
    var changed =
        send(
            first,
            "POST",
            "/settings/password",
            "old=" + PASSWORD + "&password=Changed12345&confirm=Changed12345",
            t,
            false);
    assertEquals(303, changed.statusCode());
    assertEquals(302, get(second, "/settings").statusCode());
  }

  @Test
  @Order(7)
  void invalidFilesSearchAndCursor() throws Exception {
    as(b);
    status(
        415,
        () ->
            app.upload(
                b,
                new MockMultipartFile(
                    "file", "fake.jpg", "image/jpeg", "not an image".getBytes())));
    byte[] oversized = new byte[10485761];
    status(
        413,
        () -> app.upload(b, new MockMultipartFile("file", "large.png", "image/png", oversized)));
    String fid = upload(b).id();
    for (int i = 0; i < 23; i++) note(b, "literal%_!MiXeD " + suffix + " " + i, fid);
    var page = app.feed("%_!mixed " + suffix, "", b);
    assertEquals(20, page.items().size());
    assertFalse(page.end());
    var tail = app.feed("%_!mixed " + suffix, page.next(), b);
    assertEquals(3, tail.items().size());
    assertTrue(tail.end());
    status(400, () -> app.feed("other", page.next(), b));
    var ids = new HashSet<Long>();
    page.items().forEach(v -> assertTrue(ids.add(v.id())));
    tail.items().forEach(v -> assertTrue(ids.add(v.id())));
    var privateFile = upload(b);
    SecurityContextHolder.clearContext();
    status(404, () -> app.file(privateFile.id(), null));
    as(b);
    app.deleteFile(b, Long.parseLong(privateFile.id()));
    status(404, () -> app.file(privateFile.id(), b));
  }

  @Test
  @Order(8)
  void failedDeletesAndStagingRecovery() throws Exception {
    var f = upload(b);
    long id = Long.parseLong(f.id());
    var failing = org.mockito.Mockito.mock(com.shengid.rednote.storage.FileStorage.class);
    org.mockito.Mockito.doThrow(new IOException("simulated disk failure"))
        .when(failing)
        .delete(org.mockito.ArgumentMatchers.anyString());
    var service =
        new FileService(
            fileTx, failing, new com.shengid.rednote.storage.ImageValidator(), Clock.systemUTC());
    as(b);
    service.delete(b, id);
    assertEquals("DELETING", files.findById(id).orElseThrow().state);
    status(404, () -> app.file(f.id(), b));
    fileService.cleanup();
    assertEquals("DELETED", files.findById(id).orElseThrow().state);
    String key = UUID.randomUUID().toString();
    long staging =
        fileTx.stage(
            b, key, new com.shengid.rednote.storage.ImageValidator.Image("image/png", 100, 2, 2));
    status(409, () -> app.deleteFile(b, staging));
    new TransactionTemplate(manager)
        .executeWithoutResult(
            tx -> {
              var row = files.lock(staging).orElseThrow();
              row.createdAt = Instant.now().minus(Duration.ofHours(2));
            });
    fileService.cleanup();
    assertEquals("DELETED", files.findById(staging).orElseThrow().state);
  }

  @Test
  @Order(9)
  void fileBoundaryAndPixelBudget() throws Exception {
    as(b);
    byte[] exact = Arrays.copyOf(png(), 10485760);
    var f = app.upload(b, new MockMultipartFile("file", "exact.png", "image/png", exact));
    assertEquals(10485760, files.findById(Long.parseLong(f.id())).orElseThrow().sizeBytes);
    app.deleteFile(b, Long.parseLong(f.id()));
    byte[] huge = png();
    java.nio.ByteBuffer.wrap(huge, 16, 8).putInt(10000).putInt(10000);
    var crc = new java.util.zip.CRC32();
    crc.update(huge, 12, 17);
    java.nio.ByteBuffer.wrap(huge, 29, 4).putInt((int) crc.getValue());
    status(400, () -> app.upload(b, new MockMultipartFile("file", "huge.png", "image/png", huge)));
    byte[] broken = Arrays.copyOf(png(), 40);
    status(
        415, () -> app.upload(b, new MockMultipartFile("file", "broken.png", "image/png", broken)));
  }

  @Test
  @Order(10)
  void disableWaitsForAlreadyAuthorizedWrite() throws Exception {
    var started = new CountDownLatch(1);
    var finish = new CountDownLatch(1);
    var attempting = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    var write =
        pool.submit(
            () -> {
              as(b);
              try {
                new TransactionTemplate(manager)
                    .executeWithoutResult(
                        tx -> {
                          identities.lock(b);
                          started.countDown();
                          try {
                            assertTrue(finish.await(10, TimeUnit.SECONDS));
                          } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                          }
                        });
              } finally {
                SecurityContextHolder.clearContext();
              }
            });
    assertTrue(started.await(10, TimeUnit.SECONDS));
    var disable =
        pool.submit(
            () -> {
              as(m);
              try {
                attempting.countDown();
                app.status(m, b, "禁用");
              } finally {
                SecurityContextHolder.clearContext();
              }
            });
    try {
      assertTrue(attempting.await(10, TimeUnit.SECONDS));
      assertThrows(TimeoutException.class, () -> disable.get(200, TimeUnit.MILLISECONDS));
      finish.countDown();
      write.get(10, TimeUnit.SECONDS);
      disable.get(10, TimeUnit.SECONDS);
    } finally {
      finish.countDown();
      pool.shutdownNow();
    }
    as(m);
    app.status(m, b, "正常");
  }

  @Test
  @Order(11)
  void cleanupSeesReferencesCommittedWhileWaitingForFileLock() throws Exception {
    var f = upload(b);
    long id = Long.parseLong(f.id());
    new TransactionTemplate(manager)
        .executeWithoutResult(
            tx -> {
              files.lock(id).orElseThrow().unreferencedSince =
                  Instant.now().minus(Duration.ofHours(25));
            });
    var locked = new CountDownLatch(1);
    var finish = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    var associate =
        pool.submit(
            () -> {
              as(b);
              try {
                var transaction = new TransactionTemplate(manager);
                transaction.setIsolationLevel(
                    org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
                transaction.executeWithoutResult(
                    tx -> {
                      var u = identities.lock(b);
                      files.lock(id).orElseThrow();
                      u.avatarFileId = id;
                      locked.countDown();
                      try {
                        assertTrue(finish.await(10, TimeUnit.SECONDS));
                      } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                      }
                    });
              } finally {
                SecurityContextHolder.clearContext();
              }
            });
    assertTrue(locked.await(10, TimeUnit.SECONDS));
    var cleanup = pool.submit(() -> fileTx.claim(id, Set.of()));
    try {
      assertThrows(TimeoutException.class, () -> cleanup.get(200, TimeUnit.MILLISECONDS));
      finish.countDown();
      associate.get(10, TimeUnit.SECONDS);
      assertNull(cleanup.get(10, TimeUnit.SECONDS));
      assertEquals("READY", files.findById(id).orElseThrow().state);
      assertNull(files.findById(id).orElseThrow().unreferencedSince);
    } finally {
      finish.countDown();
      pool.shutdownNow();
    }
    as(b);
    app.profile(b, new Forms.ProfileInput("测试用户", "", ""));
    app.deleteFile(b, id);
  }

  @Test
  @Order(12)
  void dashboardTotalsHistoryAndDisabledAuthors() throws Exception {
    as(m);
    var baseline = app.dashboard();
    String name = "metric_" + suffix;
    app.register(new Forms.Registration(name, PASSWORD, PASSWORD));
    long user = account(name);
    long note = note(user, "历史统计验证", upload(user).id());
    as(user);
    app.like(user, note, true);
    var root = app.comment(user, note, "根", null);
    app.comment(user, note, "回复", root.id());
    as(m);
    var added = app.dashboard();
    assertEquals(baseline.users() + 1, added.users());
    assertEquals(baseline.notes() + 1, added.notes());
    assertEquals(baseline.likes() + 1, added.likes());
    assertEquals(baseline.comments() + 2, added.comments());
    app.status(m, user, "禁用");
    var disabled = app.dashboard();
    assertEquals(added.notes(), disabled.notes());
    assertEquals(added.comments(), disabled.comments());
    app.status(m, user, "正常");
    as(user);
    app.deleteNote(user, note);
    as(m);
    var deleted = app.dashboard();
    assertEquals(baseline.notes(), deleted.notes());
    assertEquals(baseline.likes(), deleted.likes());
    assertEquals(baseline.comments(), deleted.comments());
    assertEquals(added.trends().get(6).notes(), deleted.trends().get(6).notes());
    status(403, () -> app.deleteNote(m, note));
    status(403, () -> app.status(m, m, "禁用"));
  }

  @Test
  @Order(13)
  void logoutAndMalformedJsonAreEnforcedByRealSecurityChain() throws Exception {
    var browser = browser();
    String csrf = login(browser, B);
    var malformed =
        send(browser, "POST", "/actions/notes/1/comments", "{\"text\":\" \"}", csrf, true);
    assertEquals(400, malformed.statusCode());
    assertTrue(malformed.body().contains("requestId"));
    assertTrue(malformed.body().contains("text"));
    assertEquals(302, send(browser, "POST", "/logout", "", csrf, false).statusCode());
    assertEquals(302, get(browser, "/settings").statusCode());
    assertEquals(404, get(browser, "/images/nonexistent.png").statusCode());
  }

  @Test
  @Order(14)
  void concurrentCaseInsensitiveRegistrationHasOneWinner() throws Exception {
    String username = "race_" + suffix;
    var gate = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      var first = pool.submit(() -> registerRace(username, gate));
      var second = pool.submit(() -> registerRace(username.toUpperCase(Locale.ROOT), gate));
      gate.countDown();
      var statuses = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
      assertTrue(statuses.contains(200));
      assertTrue(statuses.contains(409));
    } finally {
      pool.shutdownNow();
    }
  }

  int registerRace(String name, CountDownLatch gate) throws Exception {
    gate.await();
    try {
      app.register(new Forms.Registration(name, PASSWORD, PASSWORD));
      return 200;
    } catch (UiException e) {
      return e.status;
    }
  }

  @Test
  @Order(15)
  void rootDeletionAndReplyCompetitionLeaveNoVisibleReplies() throws Exception {
    long note = note(b, "根评论并发", upload(b).id());
    as(b);
    var root = app.comment(b, note, "根", null);
    var gate = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      var deletion =
          pool.submit(
              () -> {
                as(b);
                try {
                  gate.await();
                  app.deleteComment(b, root.id());
                } catch (InterruptedException e) {
                  throw new RuntimeException(e);
                } finally {
                  SecurityContextHolder.clearContext();
                }
              });
      var reply =
          pool.submit(
              () -> {
                as(m);
                try {
                  gate.await();
                  app.comment(m, note, "并发回复", root.id());
                } catch (UiException e) {
                  assertEquals(409, e.status);
                } catch (InterruptedException e) {
                  throw new RuntimeException(e);
                } finally {
                  SecurityContextHolder.clearContext();
                }
              });
      gate.countDown();
      deletion.get(10, TimeUnit.SECONDS);
      reply.get(10, TimeUnit.SECONDS);
      assertEquals(0, app.commentCount(note));
      assertTrue(app.comments(note, 0, 0, b).items().isEmpty());
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  @Order(16)
  void finalUploadCommitRechecksIdentityAndDeletingFilesCannotBeReused() throws Exception {
    as(b);
    var old = SecurityContextHolder.getContext().getAuthentication();
    String key = UUID.randomUUID().toString();
    long staged =
        fileTx.stage(
            b, key, new com.shengid.rednote.storage.ImageValidator.Image("image/png", 100, 2, 2));
    as(m);
    app.status(m, b, "禁用");
    SecurityContextHolder.getContext().setAuthentication(old);
    status(401, () -> fileTx.ready(b, staged));
    as(m);
    app.status(m, b, "正常");
    new TransactionTemplate(manager)
        .executeWithoutResult(
            tx -> {
              files.lock(staged).orElseThrow().createdAt = Instant.now().minus(Duration.ofHours(2));
            });
    fileService.cleanup();
    assertEquals("DELETED", files.findById(staged).orElseThrow().state);
    var f = upload(b);
    long id = Long.parseLong(f.id());
    new TransactionTemplate(manager)
        .executeWithoutResult(
            tx -> {
              files.lock(id).orElseThrow().unreferencedSince =
                  Instant.now().minus(Duration.ofHours(25));
            });
    assertNotNull(fileTx.claim(id, Set.of()));
    as(b);
    status(403, () -> note(b, "禁止复用清理中图片", f.id()));
    fileService.cleanup();
    assertEquals("DELETED", files.findById(id).orElseThrow().state);
  }
}
