package com.shengid.rednote.task;

import com.shengid.rednote.service.FileService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FileCleanupTask {
  private final FileService files;

  public FileCleanupTask(FileService files) {
    this.files = files;
  }

  @Scheduled(
      fixedRateString = "${rednote.storage.cleanup-period:3600000}",
      initialDelayString = "${rednote.storage.cleanup-initial-delay:60000}")
  public void cleanup() {
    files.cleanup();
  }
}
