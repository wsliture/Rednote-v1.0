package com.shengid.rednote.service;

import com.shengid.rednote.entity.FileAsset;
import com.shengid.rednote.repository.*;
import com.shengid.rednote.storage.*;
import com.shengid.rednote.web.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class FileTransactions {
  private final FileAssetRepository files;
  private final IdentityService identity;
  private final Queries q;
  private final Clock clock;
  private final FileStorage storage;

  public FileTransactions(
      FileAssetRepository files,
      IdentityService identity,
      Queries q,
      Clock clock,
      FileStorage storage) {
    this.files = files;
    this.identity = identity;
    this.q = q;
    this.clock = clock;
    this.storage = storage;
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public long stage(Long viewer, String key, ImageValidator.Image image) {
    var u = identity.lock(viewer);
    var f = new FileAsset();
    f.uploaderId = u.id;
    f.storageKey = key;
    f.mimeType = image.mime();
    f.sizeBytes = image.bytes();
    f.width = image.width();
    f.height = image.height();
    f.state = "STAGING";
    f.createdAt = clock.instant();
    f.updatedAt = f.createdAt;
    files.saveAndFlush(f);
    return f.id;
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Views.Upload ready(Long viewer, long id) {
    identity.lock(viewer);
    var f = files.lock(id).orElseThrow();
    if (!java.util.Objects.equals(f.uploaderId, viewer)
        || !f.state.equals("STAGING")
        || !storage.exists(f.storageKey)) throw new UiException(409, "上传未完成，请重试");
    f.state = "READY";
    f.readyAt = clock.instant();
    f.updatedAt = f.readyAt;
    f.unreferencedSince = f.readyAt;
    return new Views.Upload(f.id.toString(), ViewService.url(f.id));
  }

  @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
  public Views.ImageFile read(long id, Long viewer) {
    var f = files.readLock(id).orElseThrow(() -> new UiException(404, "图片不可用"));
    if (!f.state.equals("READY")
        || (!q.publiclyReferenced(id) && !Objects.equals(f.uploaderId, viewer)))
      throw new UiException(404, "图片不可用");
    try {
      return new Views.ImageFile(storage.open(f.storageKey), f.mimeType);
    } catch (java.io.IOException e) {
      throw new UiException(404, "图片不可用");
    }
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public String requestDelete(Long viewer, long id) {
    identity.lock(viewer);
    var f = files.lock(id).orElseThrow(() -> new UiException(404, "图片不可用"));
    if (!Objects.equals(f.uploaderId, viewer)) throw new UiException(403, "只能删除本人上传的图片");
    if (f.state.equals("DELETED")) return null;
    if (f.state.equals("STAGING")) throw new UiException(409, "图片仍在上传中");
    if (q.referenced(id)) throw new UiException(409, "图片仍被引用，无法删除");
    f.state = "DELETING";
    f.updatedAt = clock.instant();
    return f.storageKey;
  }

  @Transactional(readOnly = true)
  public List<Long> candidates(long after) {
    return q.cleanupCandidates(
        clock.instant().minus(Duration.ofHours(24)),
        clock.instant().minus(Duration.ofHours(1)),
        after);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public String claim(long id, Set<String> active) {
    var f = files.lock(id).orElse(null);
    if (f == null || f.state.equals("DELETED") || active.contains(f.storageKey)) return null;
    if (f.state.equals("DELETING")) return f.storageKey;
    if (f.state.equals("READY")) {
      if (q.referenced(id)) {
        f.unreferencedSince = null;
        return null;
      }
      if (f.unreferencedSince == null
          || f.unreferencedSince.isAfter(clock.instant().minus(Duration.ofHours(24)))) return null;
    } else if (!f.state.equals("STAGING")
        || f.createdAt.isAfter(clock.instant().minus(Duration.ofHours(1)))) return null;
    f.state = "DELETING";
    f.updatedAt = clock.instant();
    return f.storageKey;
  }

  @Transactional
  public void deleted(long id) {
    var f = files.lock(id).orElseThrow();
    if (!f.state.equals("DELETING")) return;
    f.state = "DELETED";
    f.deletedAt = clock.instant();
    f.updatedAt = f.deletedAt;
  }

  @Transactional(readOnly = true)
  public boolean known(String key) {
    return q.storageKeyExists(key);
  }

  @Transactional(readOnly = true)
  public long overdueCleanup() {
    var now = clock.instant();
    return q.overdueCleanup(
        now.minus(Duration.ofHours(25)),
        now.minus(Duration.ofHours(1)),
        now.minus(Duration.ofHours(2)));
  }
}
