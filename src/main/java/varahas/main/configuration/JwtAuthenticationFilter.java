package varahas.main.configuration;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import varahas.main.services.JwtService;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter{

	@Autowired
	private JwtService jwtService;
	@Autowired
	private UserDetailsService userDetailsService;
	
	 private static final List<String> WHITE_LIST = Arrays.asList("/api/auth/");
	
	@Override
	protected void doFilterInternal(
			@NonNull HttpServletRequest request,
			@NonNull HttpServletResponse response, 
			@NonNull FilterChain filterChain)
			throws ServletException, IOException {
        System.out.println("JwtAuthenticationFilter.doFilterInternal");
        
        String requestURI = request.getRequestURI();
        if (WHITE_LIST.stream().anyMatch(requestURI::startsWith)) {
        	System.out.println("white listed");
            filterChain.doFilter(request, response);
            return;
        }
        System.out.println("not whitelisted");
		final String authorizationHeader = request.getHeader("Authorization");
		if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
			System.out.println("Authorization header not found");
			filterChain.doFilter(request, response);
			return;
		}
		
		System.out.println("Authorization header found");
		
		final String jwt = authorizationHeader.substring(7);
		final String username = jwtService.extractUsername(jwt);
		if(username!=null && SecurityContextHolder.getContext().getAuthentication() == null) {
			
			System.out.println("username found");
			
			UserDetails userDetails = userDetailsService.loadUserByUsername(username);
			if (jwtService.isTokenValid(jwt, userDetails)) {
				
				System.out.println("token is valid");
				
				UsernamePasswordAuthenticationToken authToken = 
						new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
				authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
				SecurityContextHolder.getContext().setAuthentication(authToken);
			}
		}
		
		System.out.println("filter chain");
		
		filterChain.doFilter(request, response);
	}
}
