package com.computerstore.email;

import com.computerstore.user.domain.AccountActionPurpose;

public interface TransactionalEmailService {

    void sendAccountAction(String recipient, AccountActionPurpose purpose, String rawToken);

    void sendEmailChangedNotice(String previousEmail, String newEmail);
}
