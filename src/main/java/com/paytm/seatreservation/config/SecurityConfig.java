package com.paytm.seatreservation.config;

import com.paytm.seatreservation.security.BearerTokenAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter;

    public SecurityConfig(BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter) {
        this.bearerTokenAuthenticationFilter = bearerTokenAuthenticationFilter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http.csrf(csrf -> csrf.disable())

                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth

                        // Health/readiness/liveness
                        .requestMatchers("/actuator/health/**").permitAll()

                        // Prometheus metrics
                        .requestMatchers("/actuator/prometheus").permitAll()

                        // Anyone can inspect a show
                        .requestMatchers(HttpMethod.GET, "/shows/**").permitAll()

                        // Only admin can create shows
                        .requestMatchers(HttpMethod.POST, "/shows").hasRole("ADMIN")

                        // Reservation requires authentication
                        .requestMatchers(HttpMethod.POST, "/shows/*/reserve").authenticated()

                        // Everything else requires authentication
                        .anyRequest().authenticated())

                .addFilterBefore(bearerTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}