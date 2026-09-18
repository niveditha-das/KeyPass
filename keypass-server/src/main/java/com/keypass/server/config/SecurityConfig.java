package com.keypass.server.config;

import com.keypass.server.vehicle.VehicleApiKeyFilter;
import com.keypass.server.vehicle.VehicleRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public VehicleApiKeyFilter vehicleApiKeyFilter(VehicleRepository vehicles, PasswordEncoder passwordEncoder) {
        return new VehicleApiKeyFilter(vehicles, passwordEncoder);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, VehicleApiKeyFilter vehicleFilter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/api/v1/.well-known/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/actuator/health")
                        .permitAll()
                        .requestMatchers(
                                "/api/v1/vehicles/*/access-checks",
                                "/api/v1/vehicles/*/offline-bundle",
                                "/api/v1/vehicles/*/offline-events",
                                "/api/v1/vehicles/*/revocations")
                        .hasRole("VEHICLE")
                        .requestMatchers("/api/v1/admin/**")
                        .hasRole("FLEET_ADMIN")
                        .anyRequest()
                        .authenticated())
                .addFilterBefore(vehicleFilter, BearerTokenAuthenticationFilter.class)
                .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.jwtAuthenticationConverter(rolesConverter())))
                .build();
    }

    private JwtAuthenticationConverter rolesConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
