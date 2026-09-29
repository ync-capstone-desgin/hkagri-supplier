package com.hankooknongsan.supplier.auth;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * 보안 설정: 세션 기반 로그인 + 역할 3종 권한.
 * <ul>
 *   <li>조회(GET): 로그인한 사용자 모두</li>
 *   <li>등록·수정: ADMIN, MANAGER</li>
 *   <li>기준정보 변경·시연 도구(/api/admin): ADMIN</li>
 * </ul>
 * PoC이므로 CSRF 보호는 끄고, 실제 서비스 전환 시 쿠키 기반 CSRF 토큰을 적용한다.
 */
@Configuration
public class SecurityConfig {

    /**
     * URL·HTTP 메서드별 접근 권한과 로그인·로그아웃 방식을 설정한다.
     * 로그인하지 않은 API 요청은 로그인 화면으로 이동시키지 않고 401을 돌려준다(SPA 방식).
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .securityContext(sc -> sc.securityContextRepository(securityContextRepository()))
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .logout(l -> l.logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler()))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/auth/login", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/settings/**").hasRole("ADMIN")
                        .requestMatchers("/api/notifications/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/**").authenticated()
                        .requestMatchers("/api/**").hasAnyRole("ADMIN", "MANAGER")
                        .anyRequest().permitAll());
        return http.build();
    }

    /** 로그인 정보를 HTTP 세션에 저장하는 저장소를 만든다. */
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** 비밀번호 암호화기를 만든다. 기본 bcrypt, 저장값 앞의 {bcrypt} 접두어로 방식을 구분한다. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** 아이디·비밀번호를 DB 사용자와 대조하는 인증 관리자를 만든다. */
    @Bean
    AuthenticationManager authenticationManager(LoginUserService userService, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userService);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
}
