package com.shengid.rednote.preview;

import static com.shengid.rednote.web.Views.*;

import com.shengid.rednote.web.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Profile("preview")
public class PreviewStore implements RednoteApplicationPort {
  public static class Account {
    long id, version;
    String username, name, bio = "", avatar = "", role = "USER", status = "正常", password;
    LocalDateTime created = now();
  }

  static class Entry {
    long id, author, version;
    String title, body;
    List<String> images;
    LocalDateTime created = now();
    Set<Long> likes = new HashSet<>();
  }

  static class Reply {
    long id, note, author, root;
    String text, replyName = "";
    LocalDateTime created = now();
  }

  record Asset(long owner, Path path, String mime) {}

  final Map<Long, Account> accounts = new LinkedHashMap<>();
  final Map<Long, Entry> notes = new LinkedHashMap<>();
  final Map<Long, Reply> replies = new LinkedHashMap<>();
  final Map<String, Asset> assets = new HashMap<>();
  final List<LocalDateTime> published = new ArrayList<>();
  final PasswordEncoder encoder;
  final Path directory;
  long sequence = 100;

  static LocalDateTime now() {
    return LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
  }

  public PreviewStore(PasswordEncoder encoder) throws IOException {
    this.encoder = encoder;
    directory = Files.createTempDirectory("rednote-preview-");
    String[] names = {"林小夏", "木木的慢生活", "宇辰在路上", "Rednote 管理员", "小鱼"};
    String[] usernames = {"linxia", "mumu", "yuchen", "admin", "xiaoyu"};
    for (int i = 0; i < 5; i++) {
      Account a = new Account();
      a.id = i + 1;
      a.username = usernames[i];
      a.name = names[i];
      a.bio = "记录日常，也收藏生活里的小小惊喜。";
      a.password = encoder.encode("Preview123");
      if (i == 3) a.role = "ADMIN";
      if (i == 4) a.status = "禁用";
      a.created = now().minusDays(6 - i);
      accounts.put(a.id, a);
    }
    String[] images = {
      "photo-1441974231531-c6227db76b6e",
      "photo-1495474472287-4d71bcdd2085",
      "photo-1476514525535-07fb3b4ae5f1",
      "photo-1449247709967-d4461a6a6103",
      "photo-1498837167922-ddd27525d352",
      "photo-1464822759023-fed622ff2c3b",
      "photo-1449247709967-d4461a6a6103",
      "photo-1470252649378-9c29740c9fa8",
      "photo-1519681393784-d120267933ba",
      "photo-1512621776951-a57141f2eefd",
      "photo-1500534623283-312aade485b7",
      "photo-1518837695005-2083093ee35b"
    };
    String[] titles = {
      "把周末交给山野，找回生活的呼吸感 🌿",
      "一杯手冲，开启不慌不忙的早晨",
      "走进湖边小镇，时间好像慢了下来",
      "家的角落｜把日子过成喜欢的样子",
      "认真吃饭，是给自己的温柔",
      "山的那边，还是值得奔赴的山",
      "在家也能拥有一平方米的松弛感",
      "落日是今天收到的最好礼物",
      "逃离城市，去看一场星空",
      "十分钟搞定！我的工作日轻食",
      "散步的时候，遇见了整个秋天",
      "听海的日子，什么都不用想"
    };
    for (int i = 0; i < 44; i++) {
      Entry n = new Entry();
      n.id = i + 1;
      n.author = i % 3 + 1;
      n.title = titles[i % 12];
      n.body =
          n.title
              + "\n\n"
              + "最近越来越喜欢这样的日常。放下手里的事情，给自己一点留白，去感受阳光、微风和眼前的风景。\n\n"
              + "不用赶路，也不用安排得太满。和喜欢的人一起，认真记录每一个普通却闪闪发光的瞬间。\n\n"
              + "你最近发现了哪些生活里的美好？一起聊聊吧。";
      n.images =
          new ArrayList<>(
              List.of(
                  "/images/" + images[i % 12] + ".jpg",
                  "/images/" + images[(i + 2) % 12] + ".jpg"));
      n.created = now().minusHours(i * 3);
      notes.put(n.id, n);
      published.add(n.created);
    }
    Reply r = new Reply();
    r.id = 90;
    r.note = 1;
    r.author = 2;
    r.text = "喜欢这样的日常，周末也想去走走！";
    replies.put(r.id, r);
  }

  public synchronized Account account(String username) {
    return accounts.values().stream()
        .filter(a -> a.username.equalsIgnoreCase(username.strip()))
        .findFirst()
        .orElseThrow(() -> new UiException(401, "账号或密码错误"));
  }

  public synchronized long version(long id) {
    return require(id).version;
  }

  Account require(Long id) {
    Account a = accounts.get(id);
    if (a == null) throw new UiException(401, "请先登录");
    if (!a.status.equals("正常")) throw new UiException(401, "账号已禁用");
    return a;
  }

  Entry entry(long id) {
    Entry n = notes.get(id);
    if (n == null) throw new UiException(404, "笔记不存在或已删除");
    return n;
  }

  public synchronized User user(long id) {
    Account a = accounts.get(id);
    if (a == null) throw new UiException(404, "用户不存在");
    return new User(
        a.id,
        a.username,
        a.name,
        a.bio,
        a.avatar,
        a.role,
        a.status,
        a.created.toLocalDate().toString(),
        notes.values().stream().filter(n -> n.author == id).count());
  }

  Author author(long id) {
    User u = user(id);
    return new Author(u.id(), u.name(), u.bio(), u.avatar());
  }

  Note view(Entry n, Long viewer) {
    return new Note(
        n.id,
        n.title,
        n.body,
        author(n.author),
        n.images.stream().map(this::url).toList(),
        List.copyOf(n.images),
        n.created.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
        n.version,
        n.likes.size(),
        viewer != null && n.likes.contains(viewer),
        Objects.equals(viewer, n.author),
        new int[] {290, 225, 330, 265, 240}[(int) (n.id - 1) % 5]);
  }

  String url(String id) {
    return id.startsWith("/") ? id : "/preview/files/" + id;
  }

  public synchronized Note note(long id, Long viewer) {
    return view(entry(id), viewer);
  }

  List<Entry> sorted() {
    return notes.values().stream()
        .sorted(
            Comparator.<Entry, LocalDateTime>comparing(n -> n.created)
                .thenComparingLong(n -> n.id)
                .reversed())
        .toList();
  }

  public synchronized Batch feed(String q, String cursor, Long viewer) {
    q = q.strip();
    if (q.codePointCount(0, q.length()) > 100) throw new UiException(400, "搜索关键词最多 100 字，请修改后重试");
    long upper = sequence, last = Long.MAX_VALUE;
    LocalDateTime lastDate = LocalDateTime.MAX;
    if (cursor != null && !cursor.isBlank()) {
      try {
        String[] bits =
            new String(
                    Base64.getUrlDecoder().decode(cursor), java.nio.charset.StandardCharsets.UTF_8)
                .split("\\|", 4);
        upper = Long.parseLong(bits[0]);
        last = Long.parseLong(bits[1]);
        lastDate = LocalDateTime.parse(bits[2]);
        if (!bits[3].equals(q)) throw new Exception();
      } catch (Exception e) {
        throw new UiException(400, "分页参数无效，请刷新");
      }
    }
    final long u = upper, l = last;
    final LocalDateTime d = lastDate;
    final String query = q.toLowerCase(Locale.ROOT);
    List<Entry> found =
        sorted().stream()
            .filter(
                n ->
                    n.id <= u
                        && (n.created.isBefore(d) || (n.created.equals(d) && n.id < l))
                        && (n.title + "\n" + n.body).toLowerCase(Locale.ROOT).contains(query))
            .limit(21)
            .toList();
    boolean end = found.size() <= 20;
    List<Entry> batch = found.stream().limit(20).toList();
    Entry tail = batch.isEmpty() ? null : batch.get(batch.size() - 1);
    String next =
        end
            ? ""
            : Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                    (upper + "|" + tail.id + "|" + tail.created + "|" + q)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    return new Batch(
        batch.stream().map(n -> view(n, viewer)).toList(),
        next,
        end,
        notes.values().stream()
            .filter(
                n ->
                    n.id <= u && (n.title + "\n" + n.body).toLowerCase(Locale.ROOT).contains(query))
            .count());
  }

  public synchronized Page<Note> profile(long id, int page, Long viewer) {
    user(id);
    return page(
        sorted().stream().filter(n -> n.author == id).map(n -> view(n, viewer)).toList(), page);
  }

  <T> Page<T> page(List<T> values, int p) {
    if (p < 0) throw new UiException(400, "页码无效");
    return new Page<>(
        values.stream().skip((long) p * 20).limit(20).toList(),
        p,
        Math.max(1, (values.size() + 19) / 20),
        values.size());
  }

  Comment view(Reply r, Long viewer) {
    return new Comment(
        r.id,
        r.root,
        author(r.author),
        r.text,
        r.replyName,
        r.created.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
        Objects.equals(viewer, r.author));
  }

  public synchronized CommentPage comments(long id, long root, int p, Long viewer) {
    entry(id);
    if (p < 0) throw new UiException(400, "页码无效");
    Comparator<Reply> order =
        Comparator.<Reply, LocalDateTime>comparing(r -> r.created).thenComparingLong(r -> r.id);
    if (root == 0) order = order.reversed();
    var found =
        replies.values().stream()
            .filter(r -> r.note == id && r.root == root)
            .sorted(order)
            .skip((long) p * 20)
            .limit(21)
            .toList();
    return new CommentPage(
        found.stream().limit(20).map(r -> view(r, viewer)).toList(),
        found.size() > 20,
        commentCount(id));
  }

  public synchronized long commentCount(long id) {
    return replies.values().stream().filter(r -> r.note == id).count();
  }

  static void check(String field, String value, int min, int max) {
    int length = value.codePointCount(0, value.length());
    if (length < min || length > max)
      throw new UiException(400, "请检查输入", Map.of(field, "请输入 " + min + "–" + max + " 个字符"));
  }

  static String text(Map<String, String> f, String k) {
    return f.getOrDefault(k, "").strip();
  }

  static void password(String p) {
    check("password", p, 8, 32);
    if (!p.matches("(?s).*[a-zA-Z].*") || !p.matches("(?s).*[0-9].*"))
      throw new UiException(400, "密码至少包含一个英文字母和一个数字", Map.of("password", "密码至少包含一个英文字母和一个数字"));
  }

  public synchronized void register(Forms.Registration command) {
    Map<String, String> f = command.values();
    String username = text(f, "username"), p = f.getOrDefault("password", "");
    if (!username.matches("[A-Za-z0-9_]{4,20}"))
      throw new UiException(400, "用户名须为 4–20 位字母、数字或下划线", Map.of("username", "用户名格式不正确"));
    if (accounts.values().stream().anyMatch(a -> a.username.equalsIgnoreCase(username)))
      throw new UiException(409, "用户名已存在", Map.of("username", "用户名已存在"));
    password(p);
    if (!p.equals(f.get("confirm")))
      throw new UiException(400, "两次密码不一致", Map.of("confirm", "两次密码不一致"));
    Account a = new Account();
    a.id = ++sequence;
    a.username = username;
    a.name = username;
    a.password = encoder.encode(p);
    accounts.put(a.id, a);
  }

  public synchronized long saveNote(Long viewer, Long id, Forms.NoteInput command) {
    Map<String, String> f = command.values();
    List<String> images = command.images();
    require(viewer);
    String title = text(f, "title"), body = text(f, "body");
    check("title", title, 1, 50);
    check("body", body, 1, 5000);
    Entry n = id == null ? new Entry() : entry(id);
    if (id != null && n.author != viewer) throw new UiException(403, "无权编辑他人笔记");
    if (id != null && !String.valueOf(n.version).equals(f.get("version")))
      throw new UiException(409, "笔记已更新，请刷新后重试");
    if (images.isEmpty() || images.size() > 9 || new HashSet<>(images).size() != images.size())
      throw new UiException(400, "请选择 1–9 张不同的图片", Map.of("images", "请选择 1–9 张不同的图片"));
    for (String image : images) {
      Asset a = assets.get(image);
      if ((a == null || a.owner != viewer) && !(id != null && n.images.contains(image)))
        throw new UiException(403, "图片不可用或不属于本人");
    }
    if (id == null) {
      n.id = ++sequence;
      n.author = viewer;
      published.add(n.created);
    }
    n.title = title;
    n.body = body;
    n.images = new ArrayList<>(images);
    n.version++;
    notes.put(n.id, n);
    return n.id;
  }

  public synchronized void profile(Long viewer, Forms.ProfileInput command) {
    Map<String, String> f = command.values();
    Account a = require(viewer);
    String name = text(f, "name"), bio = text(f, "bio"), avatar = text(f, "avatar");
    check("name", name, 1, 20);
    check("bio", bio, 0, 200);
    if (!avatar.isEmpty() && !avatar.equals(a.avatar)) {
      Asset asset = assets.get(avatar.replace("/preview/files/", ""));
      if (asset == null || asset.owner != viewer) throw new UiException(403, "头像不属于本人");
    }
    a.name = name;
    a.bio = bio;
    a.avatar = avatar.isEmpty() ? "" : url(avatar);
  }

  public synchronized void password(Long viewer, Forms.PasswordInput command) {
    Map<String, String> f = command.values();
    Account a = require(viewer);
    String p = f.getOrDefault("password", "");
    if (!encoder.matches(f.getOrDefault("old", ""), a.password))
      throw new UiException(400, "旧密码错误", Map.of("old", "旧密码错误"));
    password(p);
    if (encoder.matches(p, a.password)) throw new UiException(400, "新密码不能与旧密码相同");
    if (!p.equals(f.get("confirm"))) throw new UiException(400, "两次密码不一致");
    a.password = encoder.encode(p);
    a.version++;
  }

  public synchronized Note like(Long viewer, long id, boolean liked) {
    require(viewer);
    Entry n = entry(id);
    if (liked) n.likes.add(viewer);
    else n.likes.remove(viewer);
    return view(n, viewer);
  }

  public synchronized Comment comment(Long viewer, long id, String text, Long target) {
    require(viewer);
    entry(id);
    text = text == null ? "" : text.strip();
    check("text", text, 1, 500);
    Reply r = new Reply();
    r.id = ++sequence;
    r.note = id;
    r.author = viewer;
    r.text = text;
    if (target != null) {
      Reply t = replies.get(target);
      if (t == null || t.note != id) throw new UiException(409, "回复目标已删除，请刷新");
      r.root = t.root == 0 ? t.id : t.root;
      r.replyName = user(t.author).name();
    }
    replies.put(r.id, r);
    return view(r, viewer);
  }

  public synchronized void deleteComment(Long viewer, long id) {
    require(viewer);
    Reply r = replies.get(id);
    if (r == null) throw new UiException(404, "评论已删除");
    if (r.author != viewer) throw new UiException(403, "只能删除自己的评论");
    replies.values().removeIf(x -> x.id == id || x.root == id);
  }

  public synchronized void deleteNote(Long viewer, long id) {
    require(viewer);
    Entry n = entry(id);
    if (n.author != viewer) throw new UiException(403, "只能删除自己的笔记");
    notes.remove(id);
    replies.values().removeIf(r -> r.note == id);
  }

  public synchronized Upload upload(Long viewer, MultipartFile file) {
    require(viewer);
    if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024)
      throw new UiException(413, "单张图片不能超过 10 MB");
    try {
      byte[] bytes = file.getBytes();
      String mime =
          bytes.length > 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216
              ? "image/jpeg"
              : bytes.length > 8 && bytes[0] == (byte) 137 && bytes[1] == 80
                  ? "image/png"
                  : bytes.length > 12
                          && new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)
                              .equals("RIFF")
                          && new String(bytes, 8, 4, java.nio.charset.StandardCharsets.US_ASCII)
                              .equals("WEBP")
                      ? "image/webp"
                      : "";
      if (mime.isEmpty() || ImageIO.read(new ByteArrayInputStream(bytes)) == null)
        throw new UiException(415, "图片格式不支持或文件损坏");
      String key = UUID.randomUUID().toString();
      Path path = directory.resolve(key);
      Files.write(path, bytes);
      assets.put(key, new Asset(viewer, path, mime));
      return new Upload(key, url(key));
    } catch (IOException e) {
      throw new UiException(500, "图片保存失败，请重试");
    }
  }

  @jakarta.annotation.PreDestroy
  void cleanup() throws IOException {
    try (var paths = Files.list(directory)) {
      for (Path file : paths.toList()) Files.deleteIfExists(file);
    }
    Files.deleteIfExists(directory);
  }

  public synchronized ImageFile file(String key, Long viewer) {
    Asset a = assets.get(key);
    boolean referenced =
        notes.values().stream().anyMatch(n -> n.images.contains(key))
            || accounts.values().stream().anyMatch(u -> u.avatar.equals(url(key)));
    if (a == null || (!referenced && !Objects.equals(viewer, a.owner)))
      throw new UiException(404, "图片不可用");
    return new ImageFile(new org.springframework.core.io.FileSystemResource(a.path), a.mime);
  }

  public synchronized Page<User> users(String username, String name, String status, int p) {
    return page(
        accounts.values().stream()
            .filter(
                a ->
                    a.username
                            .toLowerCase(Locale.ROOT)
                            .contains(username.strip().toLowerCase(Locale.ROOT))
                        && a.name
                            .toLowerCase(Locale.ROOT)
                            .contains(name.strip().toLowerCase(Locale.ROOT))
                        && (status.isBlank() || a.status.equals(status)))
            .sorted(
                Comparator.<Account, LocalDateTime>comparing(a -> a.created)
                    .thenComparingLong(a -> a.id)
                    .reversed())
            .map(a -> user(a.id))
            .toList(),
        p);
  }

  public synchronized User status(Long viewer, long id, String status) {
    if (!require(viewer).role.equals("ADMIN")) throw new UiException(403, "仅管理员可操作");
    Account a = accounts.get(id);
    if (a == null) throw new UiException(404, "用户不存在");
    if (a.role.equals("ADMIN")) throw new UiException(403, "管理员账号不支持禁用");
    if (status == null || !List.of("正常", "禁用").contains(status)) throw new UiException(400, "状态无效");
    if (!a.status.equals(status)) {
      a.status = status;
      a.version++;
    }
    return user(id);
  }

  public synchronized Dashboard dashboard() {
    List<Trend> trends = new ArrayList<>();
    for (int i = 6; i >= 0; i--) {
      LocalDate day = now().toLocalDate().minusDays(i);
      trends.add(
          new Trend(
              day.toString(),
              accounts.values().stream()
                  .filter(a -> !a.role.equals("ADMIN") && a.created.toLocalDate().equals(day))
                  .count(),
              published.stream().filter(d -> d.toLocalDate().equals(day)).count()));
    }
    return new Dashboard(
        accounts.values().stream().filter(a -> !a.role.equals("ADMIN")).count(),
        notes.size(),
        notes.values().stream().mapToLong(n -> n.likes.size()).sum(),
        replies.size(),
        now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
        trends);
  }
}
