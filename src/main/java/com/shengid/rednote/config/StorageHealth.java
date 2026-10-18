package com.shengid.rednote.config;

import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.*;
import org.springframework.stereotype.Component;

@Component("uploadStorage")
public class StorageHealth implements HealthIndicator {
  private final Path root;

  public StorageHealth(@Value("${rednote.storage.root}") String path) {
    root = Path.of(path);
  }

  public Health health() {
    try {
      long usable = Files.getFileStore(root).getUsableSpace();
      return (Files.isDirectory(root) && Files.isWritable(root) && usable > 104857600
              ? Health.up()
              : Health.down())
          .withDetail("usableBytes", usable)
          .build();
    } catch (Exception e) {
      return Health.down().build();
    }
  }
}
