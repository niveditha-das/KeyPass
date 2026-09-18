package com.keypass.server.vehicle;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates a car as ROLE_VEHICLE from the X-Vehicle-Key header, matched against the VIN in
 * the request path. Real cars would use mutual TLS with a per-car certificate instead of a
 * shared-secret header; this is a simulation-friendly stand-in.
 */
public class VehicleApiKeyFilter extends OncePerRequestFilter {

    private static final Pattern VIN_IN_PATH = Pattern.compile("/api/v1/vehicles/([^/]+)/.*");
    public static final String HEADER = "X-Vehicle-Key";

    private final VehicleRepository vehicles;
    private final PasswordEncoder passwordEncoder;

    public VehicleApiKeyFilter(VehicleRepository vehicles, PasswordEncoder passwordEncoder) {
        this.vehicles = vehicles;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String apiKey = request.getHeader(HEADER);
        if (apiKey == null) {
            chain.doFilter(request, response);
            return;
        }

        Matcher matcher = VIN_IN_PATH.matcher(request.getRequestURI());
        Optional<Vehicle> vehicle = matcher.matches()
                ? vehicles.findByVin(matcher.group(1))
                : Optional.empty();

        if (vehicle.isEmpty() || !passwordEncoder.matches(apiKey, vehicle.get().getApiKeyHash())) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid vehicle credentials");
            return;
        }

        var authorities = List.of(new SimpleGrantedAuthority("ROLE_VEHICLE"));
        var authentication = new UsernamePasswordAuthenticationToken(vehicle.get(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(request, response);
    }
}
