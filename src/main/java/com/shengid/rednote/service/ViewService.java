package com.shengid.rednote.service;

import com.shengid.rednote.entity.*;
import com.shengid.rednote.repository.*;
import com.shengid.rednote.web.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ViewService {
  private final Queries q;

  public ViewService(Queries q) {
    this.q = q;
  }

  public static String url(Long id) {
    return id == null ? "/images/avatar-default.svg" : "/files/" + id;
  }

  public static String date(Instant t) {
    return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.of("Asia/Shanghai"))
        .format(t);
  }

  public Views.Author author(AppUser u) {
    return new Views.Author(u.id, u.nickname, u.bio, url(u.avatarFileId));
  }

  public Views.User user(AppUser u, boolean management) {
    return new Views.User(
        u.id,
        management ? u.username : "",
        u.nickname,
        u.bio,
        url(u.avatarFileId),
        management ? u.role : "",
        management ? (u.status.equals("ACTIVE") ? "正常" : "禁用") : "",
        management ? date(u.createdAt) : "",
        q.noteCount(u.id));
  }

  public List<Views.Note> notes(List<NoteEntity> notes, Long viewer) {
    var ids = notes.stream().map(n -> n.id).toList();
    var authors =
        q.users(notes.stream().map(n -> n.authorId).collect(Collectors.toSet())).stream()
            .collect(Collectors.toMap(u -> u.id, Function.identity()));
    var images = q.images(ids);
    var grouped = images.stream().collect(Collectors.groupingBy(i -> i.noteId));
    var files =
        q.files(images.stream().map(i -> i.fileId).collect(Collectors.toSet())).stream()
            .collect(Collectors.toMap(f -> f.id, Function.identity()));
    var counts = q.likes(ids);
    var liked = q.liked(ids, viewer);
    return notes.stream()
        .map(
            n -> {
              var ni = grouped.getOrDefault(n.id, List.of());
              var cover = ni.isEmpty() ? null : files.get(ni.get(0).fileId);
              int height =
                  cover == null
                      ? 240
                      : (int) Math.max(160, Math.min(440, 280L * cover.height / cover.width));
              return new Views.Note(
                  n.id,
                  n.title,
                  n.body,
                  author(authors.get(n.authorId)),
                  ni.stream().map(i -> url(i.fileId)).toList(),
                  ni.stream().map(i -> i.fileId.toString()).toList(),
                  date(n.publishedAt),
                  n.version,
                  Math.toIntExact(counts.getOrDefault(n.id, 0L)),
                  liked.contains(n.id),
                  Objects.equals(viewer, n.authorId),
                  height);
            })
        .toList();
  }

  public List<Views.Comment> comments(List<CommentEntity> comments, Long viewer) {
    var ids = new HashSet<Long>();
    for (var c : comments) {
      ids.add(c.authorId);
      if (c.replyToUserId != null) ids.add(c.replyToUserId);
    }
    var users = q.users(ids).stream().collect(Collectors.toMap(u -> u.id, Function.identity()));
    return comments.stream()
        .map(
            c ->
                new Views.Comment(
                    c.id,
                    c.rootCommentId == null ? 0 : c.rootCommentId,
                    author(users.get(c.authorId)),
                    c.content,
                    c.replyToUserId == null ? "" : users.get(c.replyToUserId).nickname,
                    date(c.createdAt),
                    Objects.equals(viewer, c.authorId)))
        .toList();
  }
}
