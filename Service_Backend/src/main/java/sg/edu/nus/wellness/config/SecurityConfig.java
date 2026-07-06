// Author: Xia Zihang, Yutong Luo
package sg.edu.nus.wellness.config;
import sg.edu.nus.wellness.repository.UserRepo;
import sg.edu.nus.wellness.security.*;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtTokenProvider jwt, UserRepo users) {
        return new JwtAuthFilter(jwt, users);
    }

    @Bean
    public SecurityFilterChain chain(HttpSecurity http, GatewayFilter gw, JwtAuthFilter jwt) throws Exception {
        http.csrf(c -> c.disable())
            .formLogin(f -> f.disable())
            .httpBasic(b -> b.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(a -> a
                    .requestMatchers("/error").permitAll()
                    .requestMatchers("/", "/web/**", "/css/**", "/js/**", "/images/**", "/uploads/**").permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/register", "/login", "/auth/google").permitAll()
                    .anyRequest().authenticated())
            .addFilterBefore(gw, AuthorizationFilter.class)
            .addFilterAfter(jwt, GatewayFilter.class);
        return http.build();
    }
}
