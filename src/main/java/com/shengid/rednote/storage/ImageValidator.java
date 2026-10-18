package com.shengid.rednote.storage;

import com.shengid.rednote.web.UiException;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;
import javax.imageio.*;
import org.springframework.stereotype.Component;

@Component
public class ImageValidator {
  public record Image(String mime, long bytes, int width, int height) {}

  private final Semaphore decoders = new Semaphore(2, true);

  public Image validate(Path p) throws IOException {
    boolean acquired = false;
    try {
      acquired = decoders.tryAcquire(10, TimeUnit.SECONDS);
      if (!acquired) throw new UiException(409, "图片处理繁忙，请稍后重试");
      try (var input = ImageIO.createImageInputStream(p.toFile())) {
        var readers = ImageIO.getImageReaders(input);
        if (!readers.hasNext()) throw invalid();
        var reader = readers.next();
        try {
          reader.setInput(input, true, true);
          String format = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
          String mime =
              switch (format) {
                case "jpeg", "jpg" -> "image/jpeg";
                case "png" -> "image/png";
                case "webp" -> "image/webp";
                default -> throw invalid();
              };
          int width = reader.getWidth(0), height = reader.getHeight(0);
          if (width <= 0 || height <= 0 || (long) width * height > 40000000)
            throw new UiException(400, "图片最多支持 4000 万像素");
          // Treat decoder warnings (including truncated JPEG recovery) as damaged input.
          boolean[] warning = {false};
          reader.addIIOReadWarningListener((r, message) -> warning[0] = true);
          var decoded = reader.read(0);
          if (decoded == null || warning[0]) throw invalid();
          decoded.flush();
          return new Image(mime, Files.size(p), width, height);
        } finally {
          reader.dispose();
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new UiException(409, "图片处理被中断，请重试");
    } catch (IOException | RuntimeException e) {
      if (e instanceof UiException ui) throw ui;
      throw invalid();
    } finally {
      if (acquired) decoders.release();
    }
  }

  private UiException invalid() {
    return new UiException(415, "图片格式不支持或文件损坏");
  }
}
