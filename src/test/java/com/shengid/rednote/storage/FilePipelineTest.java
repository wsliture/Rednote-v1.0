package com.shengid.rednote.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.shengid.rednote.service.*;
import com.shengid.rednote.web.UiException;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.time.Clock;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class FilePipelineTest {
  @TempDir Path dir;

  Path image() throws IOException {
    Path path = dir.resolve("valid.png");
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", path.toFile());
    return path;
  }

  @Test
  void diskMoveFailureKeepsStagingForRecovery() throws Exception {
    var storage = mock(FileStorage.class);
    var tx = mock(FileTransactions.class);
    var path = image();
    when(storage.stage(anyString(), any())).thenReturn(path);
    when(tx.stage(eq(1L), anyString(), any())).thenReturn(42L);
    doThrow(new IOException("move failed")).when(storage).publish(anyString());
    var service = new FileService(tx, storage, new ImageValidator(), Clock.systemUTC());
    var error =
        assertThrows(
            UiException.class,
            () -> service.upload(1L, new MockMultipartFile("file", Files.readAllBytes(path))));
    assertEquals(500, error.status);
    verify(tx, never()).ready(any(), anyLong());
    verify(storage, never()).discardTemporary(anyString());
  }

  @Test
  void databaseUnavailableNeverTriggersOrphanDeletion() throws Exception {
    var storage = mock(FileStorage.class);
    var tx = mock(FileTransactions.class);
    when(tx.candidates(anyLong())).thenThrow(new IllegalStateException("database unavailable"));
    var service = new FileService(tx, storage, new ImageValidator(), Clock.systemUTC());
    assertThrows(IllegalStateException.class, service::cleanup);
    verifyNoInteractions(storage);
  }

  @Test
  void failedMetadataRegistrationCompensatesTemporaryFile() throws Exception {
    var storage = mock(FileStorage.class);
    var tx = mock(FileTransactions.class);
    var path = image();
    when(storage.stage(anyString(), any())).thenReturn(path);
    when(tx.stage(eq(1L), anyString(), any())).thenThrow(new UiException(401, "account revoked"));
    var service = new FileService(tx, storage, new ImageValidator(), Clock.systemUTC());
    assertEquals(
        401,
        assertThrows(
                UiException.class,
                () -> service.upload(1L, new MockMultipartFile("file", Files.readAllBytes(path))))
            .status);
    verify(storage).discardTemporary(anyString());
    verify(storage, never()).publish(anyString());
  }
}
