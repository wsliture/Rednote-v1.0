package com.shengid.rednote.service;

import com.shengid.rednote.entity.FileAsset;
import com.shengid.rednote.repository.*;
import com.shengid.rednote.web.UiException;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class FileReferences {
  private final FileAssetRepository files;
  private final Queries q;
  private final Clock clock;

  public FileReferences(FileAssetRepository files, Queries q, Clock clock) {
    this.files = files;
    this.q = q;
    this.clock = clock;
  }

  public List<FileAsset> lock(Collection<Long> ids) {
    var result = new ArrayList<FileAsset>();
    for (long id : new TreeSet<>(ids))
      result.add(files.lock(id).orElseThrow(() -> new UiException(404, "图片不可用")));
    return result;
  }

  public void usable(FileAsset f, long user) {
    if (!Objects.equals(f.uploaderId, user) || !f.state.equals("READY"))
      throw new UiException(403, "图片不可用或不属于本人");
  }

  public void refresh(List<FileAsset> files) {
    // Call after flushing relationship changes; RC guarantees fresh reference reads.
    for (var f : files) {
      boolean used = q.referenced(f.id);
      if (used) f.unreferencedSince = null;
      else if (f.unreferencedSince == null) f.unreferencedSince = clock.instant();
      f.updatedAt = clock.instant();
    }
  }
}
