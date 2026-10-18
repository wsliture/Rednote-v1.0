package com.shengid.rednote.storage;

import com.shengid.rednote.web.UiException;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.stereotype.Component;

@Component
public class LocalFileStorage implements FileStorage {
  private final Path root, temp;
  // Bounded striped locks coordinate open streams and unlink on all supported operating systems.
  private final ReadWriteLock[] locks = new ReadWriteLock[64];

  public LocalFileStorage(@Value("${rednote.storage.root}") String directory) throws IOException {
    Path configured = Path.of(directory).toAbsolutePath().normalize();
    if (!Path.of(directory).isAbsolute()) throw new IllegalArgumentException("上传目录必须是绝对路径");
    if (Files.isSymbolicLink(configured)) throw new IOException("上传根目录不允许符号链接");
    Files.createDirectories(configured);
    root = configured.toRealPath();
    temp = root.resolve("tmp");
    Files.createDirectories(temp);
    rejectLinks(temp);
    for (int i = 0; i < locks.length; i++) locks[i] = new ReentrantReadWriteLock();
  }

  private static void rejectLinks(Path path) throws IOException {
    for (Path p = path; p != null; p = p.getParent())
      if (Files.isSymbolicLink(p)) throw new IOException("上传路径不允许符号链接");
  }

  private Path path(String key, boolean temporary) throws IOException {
    if (key == null || !key.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
      throw new IOException("存储键无效");
    Path p = (temporary ? temp : root).resolve(key).normalize();
    if (!p.startsWith(root)) throw new IOException("路径越界");
    rejectLinks(p);
    return p;
  }

  private ReadWriteLock lock(String key) {
    return locks[(key.hashCode() & Integer.MAX_VALUE) % locks.length];
  }

  public Path stage(String key, InputStream input) throws IOException {
    Path p = path(key, true);
    try (var out =
        Files.newOutputStream(p, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
      byte[] buffer = new byte[8192];
      long total = 0;
      int n;
      while ((n = input.read(buffer)) != -1) {
        total += n;
        if (total > 10485760) throw new UiException(413, "单张图片不能超过 10 MB");
        out.write(buffer, 0, n);
      }
    } catch (Exception e) {
      Files.deleteIfExists(p);
      throw e;
    }
    return p;
  }

  public void publish(String key) throws IOException {
    try {
      Files.move(path(key, true), path(key, false), StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(path(key, true), path(key, false));
    }
  }

  public boolean exists(String key) {
    try {
      return Files.isRegularFile(path(key, false), LinkOption.NOFOLLOW_LINKS);
    } catch (IOException e) {
      return false;
    }
  }

  public Resource open(String key) throws IOException {
    Lock read = lock(key).readLock();
    read.lock();
    try {
      var stream = Files.newInputStream(path(key, false), LinkOption.NOFOLLOW_LINKS);
      return new InputStreamResource(
          new FilterInputStream(stream) {
            private boolean closed;

            public void close() throws IOException {
              if (!closed) {
                closed = true;
                try {
                  super.close();
                } finally {
                  read.unlock();
                }
              }
            }
          });
    } catch (Exception e) {
      read.unlock();
      throw e;
    }
  }

  public void delete(String key) throws IOException {
    Lock write = lock(key).writeLock();
    write.lock();
    try {
      Files.deleteIfExists(path(key, false));
      Files.deleteIfExists(path(key, true));
    } finally {
      write.unlock();
    }
  }

  public void discardTemporary(String key) throws IOException {
    Files.deleteIfExists(path(key, true));
  }

  public List<Path> olderThan(Instant time) throws IOException {
    var result = new ArrayList<Path>();
    for (Path directory : List.of(root, temp)) {
      rejectLinks(directory);
      try (var stream = Files.list(directory)) {
        for (var p : stream.toList())
          if (Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)
              && Files.getLastModifiedTime(p, LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(time))
            result.add(p);
      }
    }
    return result;
  }

  public void deleteOrphan(Path p) throws IOException {
    if (!p.getParent().equals(root) && !p.getParent().equals(temp)) throw new IOException("路径越界");
    rejectLinks(p);
    Files.deleteIfExists(p);
  }
}
