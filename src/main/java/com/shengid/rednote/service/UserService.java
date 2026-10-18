package com.shengid.rednote.service;

import com.shengid.rednote.entity.*;
import com.shengid.rednote.repository.*;
import com.shengid.rednote.web.*;
import java.time.Clock;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class UserService {
  private final AppUserRepository users;
  private final Queries q;
  private final IdentityService identity;
  private final FileReferences refs;
  private final PasswordEncoder encoder;
  private final Clock clock;

  public UserService(
      AppUserRepository users,
      Queries q,
      IdentityService identity,
      FileReferences refs,
      PasswordEncoder encoder,
      Clock clock) {
    this.users = users;
    this.q = q;
    this.identity = identity;
    this.refs = refs;
    this.encoder = encoder;
    this.clock = clock;
  }

  @Transactional
  public void register(Forms.Registration f) {
    String name = Rules.text("username", f.username(), 4, 20);
    if (!name.matches("[A-Za-z0-9_]{4,20}"))
      throw new UiException(400, "用户名须为 4–20 位字母、数字或下划线", Map.of("username", "用户名格式不正确"));
    Rules.password(f.password());
    if (!Objects.equals(f.password(), f.confirm()))
      throw new UiException(400, "两次密码不一致", Map.of("confirm", "两次密码不一致"));
    if (q.account(name.toLowerCase(Locale.ROOT)).isPresent())
      throw new UiException(409, "用户名已存在", Map.of("username", "用户名已存在"));
    var u = new AppUser();
    u.username = name;
    u.usernameNormalized = name.toLowerCase(Locale.ROOT);
    u.passwordHash = encoder.encode(f.password());
    u.nickname = name;
    u.bio = "";
    u.role = "USER";
    u.status = "ACTIVE";
    u.createdAt = clock.instant();
    u.updatedAt = u.createdAt;
    try {
      users.saveAndFlush(u);
    } catch (org.springframework.dao.DataIntegrityViolationException e) {
      throw new UiException(409, "用户名已存在", Map.of("username", "用户名已存在"));
    }
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void profile(Long viewer, Forms.ProfileInput f) {
    var u = identity.lock(viewer);
    String name = Rules.text("name", f.name(), 1, 20),
        bio = Rules.text("bio", f.bio(), 0, 200),
        avatar = f.avatar() == null ? "" : f.avatar().strip();
    Long next;
    if (avatar.isEmpty()) next = null;
    else if (avatar.equals(ViewService.url(u.avatarFileId))) next = u.avatarFileId;
    else next = Rules.id(avatar);
    var ids = new HashSet<Long>();
    if (next != null) ids.add(next);
    if (u.avatarFileId != null) ids.add(u.avatarFileId);
    var locked = refs.lock(ids);
    for (var file : locked) if (file.id.equals(next)) refs.usable(file, u.id);
    u.nickname = name;
    u.bio = bio;
    u.avatarFileId = next;
    u.updatedAt = clock.instant();
    users.flush();
    refs.refresh(locked);
  }

  @Transactional
  public void password(Long viewer, Forms.PasswordInput f) {
    var u = identity.lock(viewer);
    if (f.old() == null || !encoder.matches(f.old(), u.passwordHash))
      throw new UiException(400, "旧密码错误", Map.of("old", "旧密码错误"));
    Rules.password(f.password());
    if (!Objects.equals(f.password(), f.confirm()))
      throw new UiException(400, "两次密码不一致", Map.of("confirm", "两次密码不一致"));
    if (encoder.matches(f.password(), u.passwordHash)) throw new UiException(400, "新密码不能与旧密码相同");
    u.passwordHash = encoder.encode(f.password());
    u.authVersion++;
    u.updatedAt = clock.instant();
  }

  @Transactional
  public void initializeAdmin(String username, String password) {
    String name = Rules.text("username", username, 4, 20);
    if (!name.matches("[A-Za-z0-9_]{4,20}")) throw new IllegalArgumentException("管理员用户名格式错误");
    Rules.password(password);
    var existing = q.account(name.toLowerCase(Locale.ROOT));
    if (existing.isPresent()) {
      if (!existing.get().role.equals("ADMIN")) throw new IllegalStateException("同名普通用户已存在，禁止升级");
      return;
    }
    var u = new AppUser();
    u.username = name;
    u.usernameNormalized = name.toLowerCase(Locale.ROOT);
    u.passwordHash = encoder.encode(password);
    u.nickname = name;
    u.bio = "";
    u.role = "ADMIN";
    u.status = "ACTIVE";
    u.createdAt = clock.instant();
    u.updatedAt = u.createdAt;
    users.saveAndFlush(u);
  }
}
