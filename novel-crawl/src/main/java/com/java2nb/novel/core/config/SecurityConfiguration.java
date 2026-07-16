package com.java2nb.novel.core.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cấu hình Spring Security
 *
 * @author Administrator
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    @Value("${admin.username}")
    private String username;

    @Value("${admin.password}")
    private String password;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public InMemoryUserDetailsManager userDetailsService() {
        UserDetails admin = User.builder()
                .username(username)
                .password(passwordEncoder().encode(password))
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(admin);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // Tắt CSRF
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/favicon.ico").permitAll() // Cho phép truy cập tài nguyên tĩnh
                        .anyRequest().hasRole("ADMIN") // Các yêu cầu khác cần vai trò ADMIN
                )
                .formLogin(form -> form
                        .loginPage("/login.html") // Trang đăng nhập tùy chỉnh
                        .loginProcessingUrl("/login") // URL xử lý đăng nhập
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout") // URL đăng xuất
                        .logoutSuccessUrl("/") // Trang chuyển đến sau khi đăng xuất thành công
                )
                .httpBasic(Customizer.withDefaults()); // Bật xác thực HTTP Basic

        return http.build();
    }
}
