package com.shengid.rednote.service;

import com.shengid.rednote.entity.*;
import com.shengid.rednote.repository.*;
import com.shengid.rednote.web.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReadService {
  private final Queries q;
  private final ViewService views;
  private final AppUserRepository users;
  private final NoteEntityRepository notes;
  private final CommentEntityRepository comments;

  public ReadService(
      Queries q,
      ViewService views,
      AppUserRepository users,
      NoteEntityRepository notes,
      CommentEntityRepository comments) {
    this.q = q;
    this.views = views;
    this.users = users;
    this.notes = notes;
    this.comments = comments;
  }

  public AppUser userEntity(long id) {
    return users.findById(id).orElseThrow(() -> new UiException(404, "用户不存在"));
  }

  public Views.PublicUser publicUser(long id) {
    var u = userEntity(id);
    return new Views.PublicUser(
        u.id, u.nickname, u.bio, ViewService.url(u.avatarFileId), q.noteCount(id));
  }

  public Views.User user(long id) {
    var a =
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .getAuthentication();
    boolean management =
        a != null
            && (a.getPrincipal() instanceof com.shengid.rednote.security.SessionUser p
                    && p.id() == id
                || a.getAuthorities().stream()
                    .anyMatch(g -> g.getAuthority().equals("ROLE_ADMIN")));
    return views.user(userEntity(id), management);
  }

  public NoteEntity entry(long id) {
    var n = notes.findById(id).orElseThrow(() -> new UiException(404, "笔记不存在或已删除"));
    if (n.deletedAt != null) throw new UiException(404, "笔记不存在或已删除");
    return n;
  }

  public Views.Note note(long id, Long viewer) {
    return views.notes(List.of(entry(id)), viewer).get(0);
  }

  public Views.Batch feed(String query, String cursor, Long viewer) {
    String text = Rules.text("q", query, 0, 100);
    long upper = q.upper(), lastId = Long.MAX_VALUE;
    Instant last = Instant.parse("9999-12-31T23:59:59Z");
    if (cursor != null && !cursor.isBlank())
      try {
        if (cursor.length() > 2048) throw new IllegalArgumentException();
        var bits =
            new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
                .split("\\|", 4);
        upper = Long.parseLong(bits[0]);
        lastId = Long.parseLong(bits[1]);
        last = Instant.parse(bits[2]);
        if (upper < 1
            || lastId < 1
            || lastId > upper
            || last.isBefore(Instant.EPOCH)
            || !bits[3].equals(text)) throw new IllegalArgumentException();
      } catch (Exception e) {
        throw new UiException(400, "分页参数无效，请刷新");
      }
    var rows = q.feed(text, upper, last, lastId);
    boolean end = rows.size() <= 20;
    var selected = rows.stream().limit(20).toList();
    String next = "";
    if (!end) {
      var tail = selected.get(selected.size() - 1);
      next =
          Base64.getUrlEncoder()
              .withoutPadding()
              .encodeToString(
                  (upper + "|" + tail.id + "|" + tail.publishedAt + "|" + text)
                      .getBytes(StandardCharsets.UTF_8));
    }
    return new Views.Batch(views.notes(selected, viewer), next, end, q.feedCount(text, upper));
  }

  public Views.Page<Views.Note> profile(long id, int page, Long viewer) {
    userEntity(id);
    long total = q.noteCount(id);
    return new Views.Page<>(
        views.notes(q.profile(id, page), viewer),
        page,
        (int) Math.max(1, (total + 19) / 20),
        total);
  }

  public long commentCount(long id) {
    entry(id);
    return q.comments(id);
  }

  public Views.CommentPage comments(long id, long root, int page, Long viewer) {
    entry(id);
    if (root < 0) throw new UiException(400, "评论标识无效");
    if (root != 0) {
      var r = comments.findById(root).orElseThrow(() -> new UiException(404, "评论已删除"));
      if (r.deletedAt != null || r.rootCommentId != null || r.noteId != id)
        throw new UiException(404, "评论已删除");
    }
    var rows = q.comments(id, root, page);
    return new Views.CommentPage(
        views.comments(rows.stream().limit(20).toList(), viewer), rows.size() > 20, q.comments(id));
  }
}
