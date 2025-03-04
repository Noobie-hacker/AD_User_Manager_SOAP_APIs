package com.example.soap_crud.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.ldap.authentication.ad.ActiveDirectoryLdapAuthenticationProvider;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class SecurityConfig {

    private static final String AD_DOMAIN = "mylab.local";
    private static final String AD_URL = "ldaps://WIN-KURQEVBDCT8.mylab.local:636";
    private static final String REQUIRED_GROUP = "CN=API_Users,DC=mylab,DC=local";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().hasAuthority("ROLE_API_USERS")
                )
                .httpBasic()
                .and()
                .csrf().disable(); // Disable CSRF for SOAP services

        return http.build();
    }

    @Bean
    public ActiveDirectoryLdapAuthenticationProvider authenticationProvider() {
        ActiveDirectoryLdapAuthenticationProvider provider =
                new ActiveDirectoryLdapAuthenticationProvider(AD_DOMAIN, AD_URL);

        provider.setConvertSubErrorCodesToExceptions(true);
        provider.setUseAuthenticationRequestCredentials(true);

        // Map authorities for group membership validation
        provider.setAuthoritiesMapper(this::mapToApiUserRole);

        return provider;
    }

    private Collection<? extends GrantedAuthority> mapToApiUserRole(Collection<? extends GrantedAuthority> authorities) {
        authorities.forEach(authority -> System.out.println("Authority: " + authority.getAuthority()));

        List<GrantedAuthority> mappedAuthorities = authorities.stream()
                .peek(authority -> System.out.println("Checking Authority: " + authority.getAuthority()))
                .filter(authority -> authority.getAuthority().equals("API_Users"))
                .map(authority -> {
                    System.out.println("Matched Required Group: " + authority.getAuthority());
                    return new SimpleGrantedAuthority("ROLE_API_USERS");
                })
                .collect(Collectors.toList());

        mappedAuthorities.forEach(authority -> System.out.println("Mapped Authority: " + authority.getAuthority()));

        return mappedAuthorities;
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager(); // Only AD users are authenticated
    }
}
