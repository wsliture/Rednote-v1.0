package com.shengid.rednote.security;

import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Minimal serializable session identity; credentials are erased after authentication. */
public final class SessionUser implements UserDetails, CredentialsContainer {
  private final long id;
  private final long version;
  private final String username;
  private String password;
  private final List<GrantedAuthority> authorities;
  private final boolean enabled;

  public SessionUser(
      long id,
      long version,
      String username,
      String password,
      List<GrantedAuthority> authorities,
      boolean enabled) {
    this.id = id;
    this.version = version;
    this.username = username;
    this.password = password;
    this.authorities = List.copyOf(authorities);
    this.enabled = enabled;
  }

  public long id() {
    return id;
  }

  public long version() {
    return version;
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }

  public List<GrantedAuthority> getAuthorities() {
    return authorities;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void eraseCredentials() {
    password = null;
  }
}
