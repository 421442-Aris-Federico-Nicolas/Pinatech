package com.computerstore.security;

import com.computerstore.user.domain.UserAccount;
import com.computerstore.user.repository.UserAccountRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;

    public CustomUserDetailsService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        UserAccount user = userAccountRepository.findByEmailIgnoreCase(email)
                .filter(UserAccount::isActive)
                .orElseThrow(() -> new UsernameNotFoundException("User not found."));
        return AuthenticatedUser.from(user);
    }

    public AuthenticatedUser loadActiveUserById(Long id) {
        UserAccount user = userAccountRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found."));
        return AuthenticatedUser.from(user);
    }
}
