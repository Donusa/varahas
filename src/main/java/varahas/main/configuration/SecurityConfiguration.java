package varahas.main.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {
	@Value("${ALLOWED_ORIGIN}")
	private String allowedOrigin;

	@Autowired
	private JwtAuthenticationFilter jwtAuthFilter;
	@Autowired
	private AuthenticationProvider authenticationProvider;
    @Autowired
    private LogoutHandler logoutHandler;
	

    private static final String[] WHITE_LIST = {
    	    "/api/auth/**",
    	    "/swagger-ui/**",
    	    "/v3/api-docs/**",
    	    "/swagger-ui.html",
    	    "/webhooks/**"
    	};

    
	@Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(WHITE_LIST).permitAll().anyRequest().authenticated();
                })
                .sessionManagement(management -> {
                    management.sessionCreationPolicy(SessionCreationPolicy.STATELESS);
                })
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(corsLoggingFilter(), jwtAuthFilter.getClass())
                .logout(logout -> logout.logoutUrl("/api/auth/logout")
                        .addLogoutHandler(logoutHandler)
                        .logoutSuccessHandler((request, response, authentication) -> SecurityContextHolder.clearContext()));
        return http.build();
    }
	
	@Bean
    UrlBasedCorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.addAllowedOrigin(allowedOrigin);
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        source.registerCorsConfiguration("/**", config);
        return source;
    }
    
    @Bean
    Filter corsLoggingFilter() {
        return new Filter() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                    throws IOException, ServletException {
                
                HttpServletRequest httpRequest = (HttpServletRequest) request;
                HttpServletResponse httpResponse = (HttpServletResponse) response;
                
                String origin = httpRequest.getHeader("Origin");
                String method = httpRequest.getMethod();
                String uri = httpRequest.getRequestURI();
                
                if (origin != null) {
                    System.out.println("🌐 Request CORS: origin=" + origin + ", method=" + method + ", uri=" + uri + ", allowedOrigin=" + allowedOrigin);
                    
                    if (!allowedOrigin.equals("*") && !allowedOrigin.equals(origin)) {
                        System.out.println("❌ CORS BLOCKED: origin=" + origin + " no coincide con allowedOrigin=" + allowedOrigin);
                    }
                }
                
                chain.doFilter(request, response);
                
                String corsHeader = httpResponse.getHeader("Access-Control-Allow-Origin");
                if (origin != null && corsHeader == null) {
                    System.out.println("⚠️ CORS REJECTED: No se envió Access-Control-Allow-Origin para origin=" + origin);
                }
            }
        };
    }
}
