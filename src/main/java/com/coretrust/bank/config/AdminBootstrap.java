package com.coretrust.bank.config;

import com.coretrust.bank.user.entity.Role;
import com.coretrust.bank.user.entity.User;
import com.coretrust.bank.user.entity.UserStatus;
import com.coretrust.bank.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first ADMIN on startup if it doesn't exist.
 * Registration only ever creates CUSTOMERs, so without this there'd be no way to get an admin.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap.admin.name}")
    private String name;
    @Value("${app.bootstrap.admin.email}")
    private String email;
    @Value("${app.bootstrap.admin.phone}")
    private String phone;
    @Value("${app.bootstrap.admin.password}")
    private String password;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            return;
        }
        User admin = new User();
        admin.setName(name);
        admin.setEmail(normalizedEmail);
        admin.setPhone(phone);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole(Role.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        userRepository.save(admin);
        log.info("Bootstrap admin created: {}", normalizedEmail);
    }
}