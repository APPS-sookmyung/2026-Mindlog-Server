package com.apps.mindlog.global.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {
    @Bean
    FilterRegistrationBean<AccessTokenAuthenticationFilter> accessTokenFilterRegistration(AccessTokenAuthenticationFilter filter) {
        FilterRegistrationBean<AccessTokenAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemDetailsAuthenticationEntryPoint entryPoint,
            ProblemDetailsAccessDeniedHandler deniedHandler, AccessTokenAuthenticationFilter accessTokens) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .addFilterBefore(accessTokens, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/sessions", "/api/auth/sessions/refresh", "/api/auth/email-sessions", "/api/users").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/email-availability").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/emotion-characters").permitAll()
                        .anyRequest().authenticated())
                .build();
    }
}
