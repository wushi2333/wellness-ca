package sg.edu.nus.wellness.config;
import sg.edu.nus.wellness.security.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain chain(HttpSecurity http, GatewayFilter gw, JwtAuthFilter jwt) throws Exception {
        http.csrf(c -> c.disable())
            .formLogin(f -> f.disable())
            .httpBasic(b -> b.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.anyRequest().permitAll())
            .addFilterBefore(gw, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(jwt, GatewayFilter.class);
        return http.build();
    }
}
