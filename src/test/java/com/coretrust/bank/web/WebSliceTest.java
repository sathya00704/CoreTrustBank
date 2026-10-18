package com.coretrust.bank.web;

import com.coretrust.bank.security.CustomUserDetailsService;
import com.coretrust.bank.security.JwtService;
import com.coretrust.bank.security.SecurityUser;
import com.coretrust.bank.user.entity.Permission;
import com.coretrust.bank.user.entity.Role;
import com.coretrust.bank.user.entity.UserStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/**
 * Common setup for web slice tests. Subclasses add @WebMvcTest(controllers = ...) and
 * @Import of the real security configuration, plus @MockitoBean for their own services.
 */
public abstract class WebSliceTest {

    @Autowired
    protected MockMvc mockMvc;

    // Needed by the real SecurityConfig and JWT filter
    @MockitoBean
    protected JwtService jwtService;
    @MockitoBean
    protected CustomUserDetailsService userDetailsService;

    protected static SecurityUser customer(long id) {
        return user(id, Role.CUSTOMER, Set.of());
    }

    protected static SecurityUser support(long id, Permission... permissions) {
        return user(id, Role.SUPPORT, Set.of(permissions));
    }

    protected static SecurityUser admin(long id) {
        return user(id, Role.ADMIN, Set.of());
    }

    protected static SecurityUser user(long id, Role role, Set<Permission> permissions) {
        return new SecurityUser(id, role.name().toLowerCase() + id + "@test.com", null, role,
                UserStatus.ACTIVE, "session-" + id, permissions);
    }

    /** Runs the request as this user, skipping the JWT step (tested separately in JwtFilterWebTest). */
    protected static RequestPostProcessor as(SecurityUser user) {
        return authentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}