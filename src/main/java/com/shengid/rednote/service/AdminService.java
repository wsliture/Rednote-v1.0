package com.shengid.rednote.service;

import com.shengid.rednote.entity.*;
import com.shengid.rednote.repository.*;
import com.shengid.rednote.web.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class AdminService {
  private final IdentityService identity;
  private final AppUserRepository users;
  private final Queries q;
  private final ViewService views;
  private final Clock clock;

  public AdminService(
      IdentityService identity,
      AppUserRepository users,
      Queries q,
      ViewService views,
      Clock clock) {
    this.identity = identity;
    this.users = users;
    this.q = q;
    this.views = views;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public Views.Page<Views.User> users(String username, String name, String status, int page) {
    identity.admin();
    String a = Rules.text("username", username, 0, 100),
        b = Rules.text("name", name, 0, 100),
        s = Rules.status(status, true);
    long total = q.adminUserCount(a, b, s);
    return new Views.Page<>(
        q.adminUsers(a, b, s, page).stream().map(u -> views.user(u, true)).toList(),
        page,
        (int) Math.max(1, (total + 19) / 20),
        total);
  }

  @Transactional
  public Views.User status(Long viewer, long id, String value) {
    var principal = identity.principal(viewer);
    var locked = new HashMap<Long, AppUser>();
    for (long uid : new TreeSet<>(List.of(principal.id(), id)))
      locked.put(uid, users.lock(uid).orElseThrow(() -> new UiException(404, "用户不存在")));
    var actor = locked.get(principal.id());
    identity.verify(actor, principal);
    if (!actor.role.equals("ADMIN")) throw new UiException(403, "仅管理员可操作");
    var target = locked.get(id);
    if (target.role.equals("ADMIN")) throw new UiException(403, "管理员账号不支持状态操作");
    String status = Rules.status(value, false);
    if (!status.equals(target.status)) {
      target.status = status;
      if (status.equals("DISABLED")) target.authVersion++;
      target.updatedAt = clock.instant();
    }
    org.slf4j.LoggerFactory.getLogger(getClass())
        .info("user_status actor={} target={} state={}", actor.id, id, status);
    return views.user(target, true);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Views.Dashboard dashboard() {
    identity.admin();
    Instant now = clock.instant();
    var today = now.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
    var trends = new ArrayList<Views.Trend>();
    // First consistent read fixes the snapshot used by totals and daily aggregates.
    long users = q.usersTotal(),
        notes = q.notesTotal(),
        likes = q.likesTotal(),
        comments = q.commentsTotal();
    for (int i = 6; i >= 0; i--) {
      var day = today.minusDays(i);
      var start = day.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant();
      var end = day.plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant();
      trends.add(new Views.Trend(day.toString(), q.newUsers(start, end), q.published(start, end)));
    }
    return new Views.Dashboard(users, notes, likes, comments, ViewService.date(now), trends);
  }
}
