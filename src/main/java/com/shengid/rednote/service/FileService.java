package com.shengid.rednote.service;

import com.shengid.rednote.storage.*;
import com.shengid.rednote.web.*;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileService {
  private final FileTransactions tx;
  private final FileStorage storage;
  private final ImageValidator validator;
  private final Clock clock;
  private final Set<String> active = ConcurrentHashMap.newKeySet();
  private final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(getClass());

  public FileService(
      FileTransactions tx, FileStorage storage, ImageValidator validator, Clock clock) {
    this.tx = tx;
    this.storage = storage;
    this.validator = validator;
    this.clock = clock;
  }

  public Views.Upload upload(Long viewer, MultipartFile file) {
    if (file.isEmpty() || file.getSize() > 10485760) throw new UiException(413, "请选择不超过 10 MB 的图片");
    String key = UUID.randomUUID().toString();
    active.add(key);
    boolean staged = false;
    try (var input = file.getInputStream()) {
      var path = storage.stage(key, input);
      var image = validator.validate(path);
      long id = tx.stage(viewer, key, image);
      staged = true;
      storage.publish(key);
      return tx.ready(viewer, id);
    } catch (IOException e) {
      log.warn("upload_failed key={}", key, e);
      throw new UiException(500, "图片保存失败，请重试");
    } finally {
      active.remove(key);
      if (!staged)
        try {
          storage.discardTemporary(key);
        } catch (IOException e) {
          log.warn("temporary_cleanup_failed key={}", key, e);
        }
    }
  }

  public Views.ImageFile read(String id, Long viewer) {
    return tx.read(Rules.id(id), viewer);
  }

  public void delete(Long viewer, long id) {
    String key = tx.requestDelete(viewer, id);
    if (key != null) unlink(id, key);
  }

  private void unlink(long id, String key) {
    try {
      storage.delete(key);
      tx.deleted(id);
    } catch (Exception e) {
      log.warn("file_delete_retry id={}", id, e);
    }
  }

  public void cleanup() {
    long after = 0;
    for (; ; ) {
      var ids = tx.candidates(after);
      if (ids.isEmpty()) break;
      for (long id : ids) {
        after = id;
        try {
          String key = tx.claim(id, active);
          if (key != null && !active.contains(key)) unlink(id, key);
        } catch (Exception e) {
          log.warn("file_cleanup_failed id={}", id, e);
        }
      }
    }
    try {
      for (var p : storage.olderThan(clock.instant().minus(Duration.ofHours(24)))) {
        String key = p.getFileName().toString();
        if (!active.contains(key) && !tx.known(key)) storage.deleteOrphan(p);
      }
    } catch (Exception e) {
      log.warn("orphan_scan_failed; no further files removed", e);
    }
  }
}
