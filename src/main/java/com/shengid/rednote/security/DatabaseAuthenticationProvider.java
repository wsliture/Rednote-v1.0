package com.shengid.rednote.security;

import com.shengid.rednote.repository.AppUserRepository;
import com.shengid.rednote.service.IdentityService;
import java.util.List;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DatabaseAuthenticationProvider implements AuthenticationProvider {
  @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;
  private final IdentityService identity;
  private final AppUserRepository users;
  private final PasswordEncoder encoder;
  private final String dummy;

  public DatabaseAuthenticationProvider(
      IdentityService identity, AppUserRepository users, PasswordEncoder encoder) {
    this.identity = identity;
    this.users = users;
    this.encoder = encoder;
    this.dummy = encoder.encode(java.util.UUID.randomUUID().toString());
  }

  @Transactional
  public Authentication authenticate(Authentication request) {
    com.shengid.rednote.entity.AppUser initial;
    try {
      initial = identity.account(request.getName());
    } catch (org.springframework.security.core.userdetails.UsernameNotFoundException e) {
      encoder.matches(String.valueOf(request.getCredentials()), dummy);
      throw new BadCredentialsException("账号或密码错误");
    }
    var user = users.lock(initial.id).orElseThrow(() -> new BadCredentialsException("账号或密码错误"));
    em.refresh(user, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    if (!encoder.matches(String.valueOf(request.getCredentials()), user.passwordHash))
      throw new BadCredentialsException("账号或密码错误");
    if (!user.status.equals("ACTIVE")) throw new DisabledException("账号已禁用");
    var principal =
        new SessionUser(
            user.id,
            user.authVersion,
            user.username,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_" + user.role)),
            true);
    return UsernamePasswordAuthenticationToken.authenticated(
        principal, null, principal.getAuthorities());
  }

  public boolean supports(Class<?> type) {
    return UsernamePasswordAuthenticationToken.class.isAssignableFrom(type);
  }
}
