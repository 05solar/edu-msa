package com.edu.msa.security;

import com.edu.msa.common.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 플랫폼 backend 의 접근 제어.
 * 인증 자체는 auth-service 가 담당하고, 여기서는 JWT 검증 결과의 role 클레임으로 인가만 판단한다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final ObjectMapper objectMapper;
    private final String[] allowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter, ObjectMapper objectMapper,
                          @Value("${edu.cors.allowed-origins}") String origins) {
        this.jwtFilter = jwtFilter;
        this.objectMapper = objectMapper;
        this.allowedOrigins = origins.split("\\s*,\\s*");
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/health", "/api/healthz").permitAll()
                        // 분류 체계는 로그인 전 화면에서도 필요하므로 공개한다.
                        .requestMatchers(HttpMethod.GET, "/api/catalog/**").permitAll()

                        // 운영 관리자 전용 — 검토/권한/배포
                        .requestMatchers(HttpMethod.GET, "/api/programs/pending", "/api/programs/all").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/programs/*/review").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/review/logs").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/users").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/users/*/role").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/deploy", "/api/deploy/validate").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/programs/*/deploy").hasRole("ADMIN")

                        // 프로그램 등록은 바이브 코더 이상
                        .requestMatchers(HttpMethod.POST, "/api/programs").hasAnyRole("CODER", "ADMIN")

                        // 조회·의견 등록 등 나머지는 로그인한 사용자면 가능
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                write(res, HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."))
                        .accessDeniedHandler((req, res, ex) ->
                                write(res, HttpStatus.FORBIDDEN, "접근 권한이 없습니다.")))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    private void write(jakarta.servlet.http.HttpServletResponse res, HttpStatus status,
                       String message) throws java.io.IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(res.getWriter(),
                ApiError.of(status.value(), status.getReasonPhrase(), message));
    }
}
