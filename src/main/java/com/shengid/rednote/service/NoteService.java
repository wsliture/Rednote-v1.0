package com.shengid.rednote.service;

import com.shengid.rednote.entity.*;
import com.shengid.rednote.repository.*;
import com.shengid.rednote.web.*;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class NoteService {
  private final NoteEntityRepository notes;
  private final NoteImageRepository images;
  private final Queries q;
  private final IdentityService identity;
  private final FileReferences refs;
  private final Clock clock;

  public NoteService(
      NoteEntityRepository notes,
      NoteImageRepository images,
      Queries q,
      IdentityService identity,
      FileReferences refs,
      Clock clock) {
    this.notes = notes;
    this.images = images;
    this.q = q;
    this.identity = identity;
    this.refs = refs;
    this.clock = clock;
  }

  public NoteEntity lock(long id) {
    var n = notes.lock(id).orElseThrow(() -> new UiException(404, "笔记不存在或已删除"));
    if (n.deletedAt != null) throw new UiException(404, "笔记不存在或已删除");
    return n;
  }

  private void owner(NoteEntity n, long user) {
    if (n.authorId != user) throw new UiException(403, "只能管理自己的笔记");
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public long save(Long viewer, Long id, Forms.NoteInput f) {
    var u = identity.lock(viewer);
    String title = Rules.text("title", f.title(), 1, 50),
        body = Rules.text("body", f.body(), 1, 5000);
    var ids = f.images().stream().map(Rules::id).toList();
    if (ids.isEmpty() || ids.size() > 9 || new HashSet<>(ids).size() != ids.size())
      throw new UiException(400, "请选择 1–9 张不同的图片", Map.of("images", "请选择 1–9 张不同的图片"));
    NoteEntity n;
    if (id == null) {
      n = new NoteEntity();
      n.authorId = u.id;
      n.publishedAt = clock.instant();
    } else {
      n = lock(id);
      owner(n, u.id);
      try {
        if (f.version() == null || Long.parseLong(f.version()) != n.version)
          throw new NumberFormatException();
      } catch (NumberFormatException e) {
        throw new UiException(409, "笔记已更新，请刷新后重试");
      }
    }
    var all = new HashSet<>(ids);
    if (id != null) q.images(List.of(id)).forEach(i -> all.add(i.fileId));
    var locked = refs.lock(all);
    for (var file : locked) if (ids.contains(file.id)) refs.usable(file, u.id);
    n.title = title;
    n.body = body;
    n.updatedAt = clock.instant();
    notes.saveAndFlush(n);
    if (id != null) q.removeImages(id);
    for (int i = 0; i < ids.size(); i++) {
      var image = new NoteImage();
      image.noteId = n.id;
      image.fileId = ids.get(i);
      image.sortOrder = (byte) (i + 1);
      images.save(image);
    }
    images.flush();
    refs.refresh(locked);
    return n.id;
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void delete(Long viewer, long id) {
    var u = identity.lock(viewer);
    var n = notes.lock(id).orElseThrow(() -> new UiException(404, "笔记不存在或已删除"));
    owner(n, u.id);
    if (n.deletedAt != null) return;
    var comments = q.lockComments(id, null);
    var locked = refs.lock(q.images(List.of(id)).stream().map(i -> i.fileId).toList());
    var now = clock.instant();
    n.deletedAt = now;
    n.updatedAt = now;
    for (var c : comments) if (c.deletedAt == null) c.deletedAt = now;
    q.removeLikes(id);
    q.removeImages(id);
    notes.flush();
    refs.refresh(locked);
  }
}
