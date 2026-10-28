package com.activation.engine.infrastructure.security;

import com.activation.engine.infrastructure.web.TraceIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.net.URI;
import java.time.Instant;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final ObjectMapper objectMapper;
    private final boolean securityEnabled;

    public SecurityConfig(
            ObjectMapper objectMapper,
            @Value("${app.security.enabled:true}") boolean securityEnabled) {
        this.objectMapper = objectMapper;
        this.securityEnabled = securityEnabled;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (!securityEnabled) {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/requests").hasAuthority("SCOPE_requests:write")
                .requestMatchers(HttpMethod.GET, "/api/v1/requests/**").hasAuthority("SCOPE_requests:read")
                .anyRequest().authenticated()
        )
        .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(Customizer.withDefaults())
                .authenticationEntryPoint(problemAuthenticationEntryPoint())
                .accessDeniedHandler(problemAccessDeniedHandler())
        );

        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint problemAuthenticationEntryPoint() {
        return (request, response, authException) -> {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.UNAUTHORIZED, "Full authentication is required to access this resource.");
            problem.setTitle("Unauthorized");
            problem.setType(URI.create("https://api.activation-engine.io/problems/unauthorized"));
            problem.setInstance(URI.create(request.getRequestURI()));

            String traceId = MDC.get(TraceIdFilter.MDC_TRACE_ID_KEY);
            if (traceId != null) {
                problem.setProperty("traceId", traceId);
            }
            problem.setProperty("timestamp", Instant.now().toString());

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(problem));
        };
    }

    @Bean
    public AccessDeniedHandler problemAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.FORBIDDEN, "Insufficient scope or privileges to perform this operation.");
            problem.setTitle("Forbidden");
            problem.setType(URI.create("https://api.activation-engine.io/problems/forbidden"));
            problem.setInstance(URI.create(request.getRequestURI()));

            String traceId = MDC.get(TraceIdFilter.MDC_TRACE_ID_KEY);
            if (traceId != null) {
                problem.setProperty("traceId", traceId);
            }
            problem.setProperty("timestamp", Instant.now().toString());

            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(problem));
        };
    }
}
