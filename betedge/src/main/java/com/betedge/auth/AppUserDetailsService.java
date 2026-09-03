package com.betedge.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // A GOOGLE-provider account has no passwordHash (see User's own Javadoc) - treated here as
        // "not found" for password-login purposes, same as a truly nonexistent email. Spring
        // Security's DaoAuthenticationProvider (hideUserNotFoundExceptions=true by default)
        // rewrites this into the same BadCredentialsException/401 a genuinely wrong password would
        // produce, so this never leaks "this email exists but uses Google" through a different
        // error path - and it avoids passing null into UserDetails.withUsername().password(...),
        // which would throw IllegalArgumentException (a 500) instead of a clean 401.
        if (user.getPasswordHash() == null) {
            throw new UsernameNotFoundException("User has no password credential");
        }

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities("ROLE_" + user.getRole().name())
                .build();
    }
}
