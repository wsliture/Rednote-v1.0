package com.shengid.rednote.web;

import java.util.List;
import java.util.Map;

/** Explicit application commands. Password commands must never be added to a view model. */
public final class Forms {
  private Forms() {}

  static String value(Map<String, String> source, String name) {
    return source.getOrDefault(name, "");
  }

  public record Registration(String username, String password, String confirm) {
    public static Registration from(Map<String, String> f) {
      return new Registration(value(f, "username"), value(f, "password"), value(f, "confirm"));
    }

    public Map<String, String> values() {
      return Map.of("username", username, "password", password, "confirm", confirm);
    }

    @Override
    public String toString() {
      return "Registration[redacted]";
    }
  }

  public record NoteInput(String title, String body, String version, List<String> images) {
    public NoteInput {
      images = List.copyOf(images);
    }

    public static NoteInput from(Map<String, String> f, List<String> images) {
      return new NoteInput(value(f, "title"), value(f, "body"), value(f, "version"), images);
    }

    public Map<String, String> values() {
      return Map.of("title", title, "body", body, "version", version);
    }
  }

  public record ProfileInput(String name, String bio, String avatar) {
    public static ProfileInput from(Map<String, String> f) {
      return new ProfileInput(value(f, "name"), value(f, "bio"), value(f, "avatar"));
    }

    public Map<String, String> values() {
      return Map.of("name", name, "bio", bio, "avatar", avatar);
    }
  }

  public record PasswordInput(String old, String password, String confirm) {
    public static PasswordInput from(Map<String, String> f) {
      return new PasswordInput(value(f, "old"), value(f, "password"), value(f, "confirm"));
    }

    public Map<String, String> values() {
      return Map.of("old", old, "password", password, "confirm", confirm);
    }

    @Override
    public String toString() {
      return "PasswordInput[redacted]";
    }
  }
}
