package com.shengid.rednote.service;

import com.shengid.rednote.entity.*;
import com.shengid.rednote.repository.*;
import com.shengid.rednote.web.*;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InteractionService {
  private final IdentityService identity;
  private final NoteService notes;
  private final CommentEntityRepository comments;
  private final Queries q;
  private final ViewService views;
  private final Clock clock;

  public InteractionService(
      IdentityService identity,
      NoteService notes,
      CommentEntityRepository comments,
      Queries q,
      ViewService views,
      Clock clock) {
    this.identity = identity;
    this.notes = notes;
    this.comments = comments;
    this.q = q;
    this.views = views;
    this.clock = clock;
  }

  @Transactional
  public Views.Note like(Long viewer, long id, boolean liked) {
    var u = identity.lock(viewer);
    var n = notes.lock(id);
    q.like(u.id, id, liked, clock.instant());
    return views.notes(List.of(n), viewer).get(0);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Views.Comment comment(Long viewer, long id, String text, Long target) {
    identity.principal(viewer);
    var initial =
        target == null
            ? null
            : q.commentIdentity(target).orElseThrow(() -> new UiException(409, "回复目标已删除，请刷新"));
    var u = identity.lockAll(viewer, initial == null ? List.of() : List.of(initial.authorId()));
    notes.lock(id);
    String value = Rules.text("text", text, 1, 500);
    var c = new CommentEntity();
    c.noteId = id;
    c.authorId = u.id;
    c.content = value;
    c.createdAt = clock.instant();
    if (target != null) {
      long root = initial.rootId() == null ? initial.id() : initial.rootId();
      var locked = new HashMap<Long, CommentEntity>();
      for (long cid : new TreeSet<>(List.of(root, target)))
        locked.put(cid, comments.lock(cid).orElseThrow(() -> new UiException(409, "回复目标已删除，请刷新")));
      var t = locked.get(target);
      var r = locked.get(root);
      if (t.deletedAt != null
          || r.deletedAt != null
          || t.noteId != id
          || r.noteId != id
          || r.rootCommentId != null
          || !(t.id.equals(r.id) || Objects.equals(t.rootCommentId, r.id)))
        throw new UiException(409, "回复目标已删除，请刷新");
      c.rootCommentId = root;
      c.targetCommentId = target;
      c.replyToUserId = t.authorId;
    }
    comments.saveAndFlush(c);
    return views.comments(List.of(c), viewer).get(0);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void delete(Long viewer, long id) {
    var u = identity.lock(viewer);
    var initial = q.commentIdentity(id).orElseThrow(() -> new UiException(404, "评论不存在"));
    notes.lock(initial.noteId());
    var locked = q.lockComments(initial.noteId(), id);
    var c =
        locked.stream()
            .filter(r -> r.id == id)
            .findFirst()
            .orElseThrow(() -> new UiException(404, "评论不存在"));
    if (!c.authorId.equals(u.id)) throw new UiException(403, "只能删除自己的评论");
    var now = clock.instant();
    for (var row : locked) if (row.deletedAt == null) row.deletedAt = now;
  }
}
