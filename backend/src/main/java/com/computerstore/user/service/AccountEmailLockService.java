package com.computerstore.user.service;

import com.computerstore.user.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountEmailLockService {
    private final UserAccountRepository users;

    public AccountEmailLockService(UserAccountRepository users) {
        this.users = users;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(String normalizedEmail) {
        users.lockNormalizedEmail(normalizedEmail);
    }
}
