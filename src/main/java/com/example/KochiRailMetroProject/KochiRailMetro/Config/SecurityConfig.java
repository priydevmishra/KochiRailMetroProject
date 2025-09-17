package com.example.KochiRailMetroProject.KochiRailMetro.Config;

import com.example.KochiRailMetroProject.KochiRailMetro.Security.JwtAuthenticationEntryPoint;
import com.example.KochiRailMetroProject.KochiRailMetro.Security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)   // ✅ FIXED (new annotation)
public class SecurityConfig {

    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
                          JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF (since we're using JWT)
                .csrf(csrf -> csrf.disable())
                // Authorization rules
                .authorizeHttpRequests(auth -> auth
                        // ✅ Allow login & registration APIs
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // ✅ Allow Swagger & API docs
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // ✅ Allow health check
                        .requestMatchers("/api/v1/health/**").permitAll()
                        // ✅ Allow Gmail & email (public APIs you defined)
                        .requestMatchers(HttpMethod.POST, "/api/v1/gmail/sync").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/email/**").permitAll()
                        // ✅ Manager register only by Admin
                        .requestMatchers(HttpMethod.POST, "/api/v1/users/register/manager").hasRole("ADMIN")
                        // ✅ Employee register only by Manager
                        .requestMatchers(HttpMethod.POST, "/api/v1/users/register/employee").hasRole("MANAGER")
                        // Everything else
                        .anyRequest().authenticated()
                )
                // Custom entry point (handles 401s)
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                    // Special handling for Gmail sync
                    if (request.getRequestURI().equals("/api/v1/gmail/sync")
                            && request.getMethod().equals("POST")) {
                        response.setStatus(HttpServletResponse.SC_OK);
                        return;
                    }
                    jwtAuthenticationEntryPoint.commence(request, response, authException);
                }))
                // Stateless session (since using JWT)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // JWT filter
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
