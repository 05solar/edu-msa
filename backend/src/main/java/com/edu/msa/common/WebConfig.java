package com.edu.msa.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 공통 설정.
 *
 * CORS 는 Spring Security 도입(security/SecurityConfig)과 함께 시큐리티 필터 체인으로 옮겼다.
 * 여기와 양쪽에서 설정하면 프리플라이트 응답에 헤더가 중복으로 실리므로 한 곳에서만 관리한다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
}
