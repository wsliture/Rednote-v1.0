package com.shengid.rednote.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageTest {
  @TempDir Path directory;

  @Test
  void pathsAndSymlinksCannotEscapeStorage() throws Exception {
    Path root = directory.resolve("uploads"), outside = directory.resolve("outside");
    Files.writeString(outside, "protected");
    var storage = new LocalFileStorage(root.toString());
    assertThrows(
        IOException.class,
        () -> storage.stage("../outside", new ByteArrayInputStream(new byte[] {1})));
    String key = UUID.randomUUID().toString();
    Files.createSymbolicLink(root.resolve(key), outside);
    assertFalse(storage.exists(key));
    assertThrows(IOException.class, () -> storage.open(key));
    assertThrows(IOException.class, () -> storage.delete(key));
    assertEquals("protected", Files.readString(outside));
  }

  @Test
  void deletionWaitsForOpenReadStream() throws Exception {
    var storage = new LocalFileStorage(directory.toRealPath().toString());
    String key = UUID.randomUUID().toString();
    storage.stage(key, new ByteArrayInputStream(new byte[] {1, 2, 3}));
    storage.publish(key);
    var input = storage.open(key).getInputStream();
    var pool = Executors.newSingleThreadExecutor();
    var started = new CountDownLatch(1);
    try {
      var deletion =
          pool.submit(
              () -> {
                started.countDown();
                try {
                  storage.delete(key);
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              });
      assertTrue(started.await(5, TimeUnit.SECONDS));
      assertThrows(TimeoutException.class, () -> deletion.get(100, TimeUnit.MILLISECONDS));
      assertArrayEquals(new byte[] {1, 2, 3}, input.readAllBytes());
      input.close();
      deletion.get(5, TimeUnit.SECONDS);
      assertFalse(storage.exists(key));
    } finally {
      input.close();
      pool.shutdownNow();
    }
  }
}
