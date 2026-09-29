package com.coretrust.bank.security;

import com.coretrust.bank.user.repository.UserRepository;
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

    /** Used by the JWT filter on every authenticated request. */
    //called by the JWT filter on each request to fetch the current user by the id in the token.
    public SecurityUser loadUserById(Long id) {
        return userRepository.findById(id)
                .map(SecurityUser::from)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}