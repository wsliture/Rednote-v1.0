package com.shengid.rednote.config;

import com.shengid.rednote.service.FileTransactions;
import org.springframework.boot.health.contributor.*;
import org.springframework.stereotype.Component;

@Component("fileCleanup")
public class CleanupHealth implements HealthIndicator {
  private final FileTransactions files;

  public CleanupHealth(FileTransactions files) {
    this.files = files;
  }

  public Health health() {
    try {
      long overdue = files.overdueCleanup();
      return (overdue == 0 ? Health.up() : Health.down())
          .withDetail("overdueFiles", overdue)
          .build();
    } catch (Exception e) {
      return Health.down().build();
    }
  }
}
