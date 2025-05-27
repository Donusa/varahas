package varahas.main.services;


import java.io.IOException;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.exc.StreamWriteException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import varahas.main.dto.AuthDTO;
import varahas.main.dto.AuthRequest;
import varahas.main.dto.RegisterRequest;
import varahas.main.entities.Tenant;
import varahas.main.entities.Token;
import varahas.main.entities.User;
import varahas.main.enums.Roles;
import varahas.main.enums.Status;
import varahas.main.enums.TokenType;
import varahas.main.repositories.TenantRepository;
import varahas.main.repositories.TokenRepository;
import varahas.main.repositories.UserRepository;

@Service
public class AuthenticationService {

	@Autowired
	private UserRepository userRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private JwtService jwtService;
	@Autowired
	private AuthenticationManager authManager;
	@Autowired
	private TokenRepository tokenRepository;
	@Autowired
	private TenantRepository tenantRepository;

	public AuthDTO register(RegisterRequest request) {
		Roles role = switch (request.getRole()) {
		case "ROLE_USER" -> Roles.ROLE_USER;
		case "ROLE_ADMIN" -> Roles.ROLE_ADMIN;
		case "ROLE_SUPER" -> Roles.ROLE_SUPER;
		default -> throw new IllegalArgumentException("Invalid role: " + request.getRole());
		};
		Tenant tenant = tenantRepository.findByName(request.getTenantName())
	            .orElseGet(() -> {
	                Tenant newTenant = Tenant.builder()
	                        .name(request.getTenantName())
	                        .createdAt(new Date())
	                        .build();
	                return tenantRepository.save(newTenant);
	            });
		
		User user = User.builder()
				.email(request.getEmail())
				.password(passwordEncoder.encode(request.getPassword()))
				.name(request.getName())
				.username(request.getUsername())
				.roles(role)
				.status(Status.ACTIVE)
				.phone(request.getPhone())
				.tenant(tenant)
				.build();
		
		User savedUser = userRepository.save(user);
		String jwtToken = jwtService.generateToken(user);
		String refreshToken = jwtService.generateRefreshToken(user);
		saveUserToken(savedUser, jwtToken);
		return AuthDTO.builder().accessToken(jwtToken).refreshToken(refreshToken).tenant(user.getTenant().getName()).build();
	}

	private void saveUserToken(User savedUser, String jwtToken) {
		var token = Token.builder().user(savedUser).token(jwtToken).type(TokenType.BEARER).expired(false).revoked(false)
				.build();
		tokenRepository.save(token);
	}

	public AuthDTO authenticate(AuthRequest request) {
		System.out.println("AuthenticationService.authenticate");
		var user = userRepository.findByUsername(request.getUsername())
				.orElseThrow(() -> new UsernameNotFoundException("User not found"));
		authManager.
				authenticate(
						new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
						);
		var jwtToken = jwtService.generateToken(user);
		var refreshToken = jwtService.generateRefreshToken(user);
		revokeAllUserTokens(user);
		saveUserToken(user, jwtToken);
		if (user.getStatus() != Status.ACTIVE) {
			throw new IllegalArgumentException("User is not active");
		}
		return AuthDTO.builder().accessToken(jwtToken).refreshToken(refreshToken).tenant(user.getTenant().getName()).build();
	}

	private void revokeAllUserTokens(User user) {
		var validUserTokens = tokenRepository.findAllValidTokenByUser(user.getId());
		if (validUserTokens.isEmpty())
			return;
		validUserTokens.forEach(token -> {
			token.setExpired(true);
			token.setRevoked(true);
		});
		tokenRepository.saveAll(validUserTokens);
	}

	public void refreshToken(HttpServletRequest request, HttpServletResponse response)
			throws StreamWriteException, DatabindException, IOException {
		final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
		final String refreshToken;
		final String userEmail;
		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			return;
		}
		refreshToken = authHeader.substring(7);
		userEmail = jwtService.extractUsername(refreshToken);
		if (userEmail != null) {
			var user = this.userRepository.findByEmail(userEmail).orElseThrow();
			if (jwtService.isTokenValid(refreshToken, user)) {
				var accessToken = jwtService.generateToken(user);
				revokeAllUserTokens(user);
				saveUserToken(user, accessToken);
				var authResponse = AuthDTO.builder().accessToken(accessToken).refreshToken(refreshToken).build();
				new ObjectMapper().writeValue(response.getOutputStream(), authResponse);
			}
		}
	}
	
	public void logout(HttpServletRequest request, HttpServletResponse response) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return;
        }
        String accessToken = authHeader.substring(7);
        String userEmail = jwtService.extractUsername(accessToken);
        if (userEmail != null) {
            var user = userRepository.findByEmail(userEmail).orElseThrow();
            var userTokens = tokenRepository.findAllValidTokenByUser(user.getId());
            userTokens.forEach(token -> {
                token.setExpired(true);
                token.setRevoked(true);
            });
            tokenRepository.saveAll(userTokens);
        }
    }
	
	public boolean emailExists(String email) {
	    return userRepository.findByEmail(email).isPresent();
	}
	
}
