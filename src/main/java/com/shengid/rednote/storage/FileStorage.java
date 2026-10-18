package com.shengid.rednote.storage;

import java.io.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.springframework.core.io.Resource;

public interface FileStorage {
  Path stage(String key, InputStream input) throws IOException;

  void publish(String key) throws IOException;

  boolean exists(String key);

  Resource open(String key) throws IOException;

  void delete(String key) throws IOException;

  void discardTemporary(String key) throws IOException;

  List<Path> olderThan(Instant time) throws IOException;

  void deleteOrphan(Path path) throws IOException;
}
