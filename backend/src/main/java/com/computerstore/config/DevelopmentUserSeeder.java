package com.computerstore.config;

import com.computerstore.user.domain.Role;
import com.computerstore.user.domain.RoleName;
import com.computerstore.user.domain.UserAccount;
import com.computerstore.user.repository.RoleRepository;
import com.computerstore.user.repository.UserAccountRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
public class DevelopmentUserSeeder implements ApplicationRunner {

    private final UserAccountRepository userAccountRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public DevelopmentUserSeeder(
            UserAccountRepository userAccountRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userAccountRepository = userAccountRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        createUserIfMissing("Admin", "Local", "admin@computerstore.local", "Admin123!", RoleName.ADMIN);
        createUserIfMissing("Technician", "Local", "technician@computerstore.local", "Technician123!", RoleName.TECHNICIAN);
        createUserIfMissing("Customer", "Local", "customer@computerstore.local", "Customer123!", RoleName.CUSTOMER);
    }

    private void createUserIfMissing(String firstName, String lastName, String email, String password, RoleName roleName) {
        var existing = userAccountRepository.findByEmailIgnoreCase(email);
        if (existing.isPresent()) {
            if (!existing.get().isEmailVerified()) existing.get().markEmailVerified();
            return;
        }
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Development role is missing: " + roleName));
        UserAccount user = new UserAccount(firstName, lastName, email, passwordEncoder.encode(password), null);
        user.markEmailVerified();
        user.addRole(role);
        userAccountRepository.save(user);
    }
}
