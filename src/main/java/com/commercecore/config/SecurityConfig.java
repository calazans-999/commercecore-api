
package com.commercecore.config;

import com.commercecore.entity.User;
import com.commercecore.repository.UserRepository;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import com.commercecore.security.SecurityErrorHandler;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        JwtAuthenticationConverter jwtConverter,
        SecurityErrorHandler securityErrorHandler)
        throws Exception {

        http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session ->
            session.sessionCreationPolicy(
                SessionCreationPolicy.STATELESS
            )
        )
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .exceptionHandling(exceptions -> exceptions
            .authenticationEntryPoint(securityErrorHandler)
            .accessDeniedHandler(securityErrorHandler)
        )
        .authorizeHttpRequests(auth -> auth

                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/auth/register",
                    "/api/v1/auth/login"
                ).permitAll()

                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/products",
                    "/api/v1/products/**",
                    "/api/v1/categories",
                    "/api/v1/categories/**"
                ).permitAll()

                .requestMatchers(
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**",
                    "/actuator/health"
                ).permitAll()

                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/products/**",
                    "/api/v1/categories/**"
                ).hasRole("ADMIN")

                .requestMatchers(
                    HttpMethod.PUT,
                    "/api/v1/products/**",
                    "/api/v1/categories/**"
                ).hasRole("ADMIN")

                .requestMatchers(
                    HttpMethod.DELETE,
                    "/api/v1/products/**",
                    "/api/v1/categories/**"
                ).hasRole("ADMIN")

                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 ->
                oauth2.jwt(jwt ->
                    jwt.jwtAuthenticationConverter(jwtConverter)
                )
            );

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter(
            UserRepository userRepository) {

        JwtAuthenticationConverter converter =
            new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {

            Long userId;

            try {
                userId = Long.valueOf(jwt.getSubject());
            } catch (NumberFormatException exception) {
                throw new BadCredentialsException(
                    "Invalid JWT subject"
                );
            }

            User user = userRepository.findById(userId)
                .orElseThrow(() ->
                    new BadCredentialsException(
                        "User no longer exists"
                    )
                );

            if (!Boolean.TRUE.equals(user.getEnabled())) {
                throw new BadCredentialsException(
                    "Account is disabled"
                );
            }

            return List.of(
                new SimpleGrantedAuthority(
                    "ROLE_" + user.getRole().name()
                )
            );
        });

        return converter;
    }
}
