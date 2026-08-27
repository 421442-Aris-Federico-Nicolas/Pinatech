package com.computerstore.security;

import java.util.Collection;
import java.util.List;

import com.computerstore.user.domain.Role;
import com.computerstore.user.domain.UserAccount;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record AuthenticatedUser(Long id, String email, long sessionVersion,
                                Collection<? extends GrantedAuthority> authorities)
        implements UserDetails {

    public AuthenticatedUser(Long id, String email, Collection<? extends GrantedAuthority> authorities) {
        this(id, email, 0, authorities);
    }

    public static AuthenticatedUser from(UserAccount user) {
        List<GrantedAuthority> authorities = user.getRoles().stream()
                .map(Role::getName)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getSessionVersion(), authorities);
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
