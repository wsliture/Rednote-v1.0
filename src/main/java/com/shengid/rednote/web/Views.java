package com.shengid.rednote.web;

import java.util.List;
import java.util.StringJoiner;

/** Presentation-only values; never expose authentication or persistence objects. */
public final class Views {
  private Views() {}

  public record User(
      long id,
      String username,
      String name,
      String bio,
      String avatar,
      String role,
      String status,
      String date,
      long noteCount) {}

  public record Author(long id, String name, String bio, String avatar) {}

  public record Note(
      long id,
      String title,
      String body,
      Author author,
      List<String> images,
      List<String> imageIds,
      String date,
      long version,
      int likes,
      boolean liked,
      boolean owner,
      int height) {}

  public record Comment(
      long id,
      long rootId,
      Author author,
      String text,
      String replyName,
      String date,
      boolean owner) {}

  public record CommentPage(List<Comment> items, boolean hasMore, long total) {}

  public record Batch(List<Note> items, String next, boolean end, long total) {}

  public record Page<T>(List<T> items, int page, int pages, long total) {}

  public record Upload(String id, String url) {}

  public record ImageFile(org.springframework.core.io.Resource resource, String mime) {}

  public record Trend(String date, long users, long notes) {}

  public record Dashboard(
      long users, long notes, long likes, long comments, String time, List<Trend> trends) {
    public long peak() {
      return Math.max(
          5, trends.stream().mapToLong(t -> Math.max(t.users(), t.notes())).max().orElse(0));
    }

    public String userPoints() {
      return points(true);
    }

    public String notePoints() {
      return points(false);
    }

    private String points(boolean users) {
      StringJoiner result = new StringJoiner(" ");
      for (int i = 0; i < trends.size(); i++) {
        Trend t = trends.get(i);
        result.add((65 + i * 130) + "," + (235 - (users ? t.users() : t.notes()) * 200 / peak()));
      }
      return result.toString();
    }
  }

  public record Failure(String code, String message, java.util.Map<String, String> fields) {}
}
