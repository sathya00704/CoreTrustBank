package com.coretrust.bank.security;

import com.coretrust.bank.user.repository.UserRepository;
import com.coretrust.bank.config.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /** Used by Spring Security during login. Our "username" is the email. */
    //called by Spring during login to fetch the user by email.
    @Override
    public SecurityUser loadUserByUsername(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase())
                .map(SecurityUser::from)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    /** Called by the JWT filter on every request: cached, without the password hash. */
    @Cacheable(cacheNames = CacheConfig.SECURITY_USERS, key = "#id")
    public SecurityUser loadUserById(Long id) {
        return userRepository.findById(id)
                .map(SecurityUser::from)
                .map(SecurityUser::withoutPassword)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}