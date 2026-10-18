package com.shengid.rednote.service;

import com.shengid.rednote.entity.AppUser;
import com.shengid.rednote.repository.*;
import com.shengid.rednote.security.SessionUser;
import com.shengid.rednote.web.UiException;
import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService {
  private final AppUserRepository users;
  private final Queries queries;

  public IdentityService(AppUserRepository users, Queries queries) {
    this.users = users;
    this.queries = queries;
  }

  @Transactional(readOnly = true)
  public AppUser account(String username) {
    return queries
        .account(username.strip().toLowerCase(Locale.ROOT))
        .orElseThrow(
            () ->
                new org.springframework.security.core.userdetails.UsernameNotFoundException(
                    "账号或密码错误"));
  }

  @Transactional(readOnly = true)
  public boolean valid(SessionUser p) {
    return users
        .findById(p.id())
        .map(u -> u.status.equals("ACTIVE") && u.authVersion == p.version())
        .orElse(false);
  }

  /** Called inside every write transaction, before locking any other resource. */
  public AppUser lock(Long expected) {
    return lockAll(expected, List.of());
  }

  /** Include users referenced by new foreign keys to avoid implicit reverse user locks. */
  public AppUser lockAll(Long expected, Collection<Long> referencedUsers) {
    var p = principal(expected);
    var ids = new TreeSet<Long>(referencedUsers);
    ids.add(p.id());
    AppUser actor = null;
    for (long id : ids) {
      var u = users.lock(id).orElseThrow(() -> new UiException(404, "用户不存在"));
      if (id == p.id()) actor = u;
    }
    verify(actor, p);
    return actor;
  }

  public void verify(AppUser u, SessionUser p) {
    if (!u.status.equals("ACTIVE") || u.authVersion != p.version())
      throw new UiException(401, "登录已失效，请重新登录");
  }

  public SessionUser principal(Long expected) {
    var a = SecurityContextHolder.getContext().getAuthentication();
    if (a == null
        || !(a.getPrincipal() instanceof SessionUser p)
        || !Objects.equals(expected, p.id())) throw new UiException(401, "请先登录");
    return p;
  }

  public void admin() {
    var a = SecurityContextHolder.getContext().getAuthentication();
    if (a == null
        || a.getAuthorities().stream().noneMatch(g -> g.getAuthority().equals("ROLE_ADMIN")))
      throw new UiException(403, "仅管理员可访问");
  }
}
