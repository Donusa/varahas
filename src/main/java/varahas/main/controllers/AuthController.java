package varahas.main.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import varahas.main.dto.AuthDTO;
import varahas.main.dto.AuthRequest;
import varahas.main.dto.RegisterRequest;
import varahas.main.services.AuthenticationService;
import varahas.main.utils.SecurityUtils;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	@Autowired
	private AuthenticationService authService;

	@PostMapping("/register")
	public ResponseEntity<?> register(@RequestBody RegisterRequest request) {

		if (authService.emailExists(request.getEmail())) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body("Email already exists");
		}
		String challenge = SecurityUtils.passwordChallenge((request.getPassword()));
		if (!"Password is safe".equals(challenge)) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(challenge);
		}
		AuthDTO response = authService.register(request);
		return ResponseEntity.ok(response);
	}
	
	@PostMapping("/authenticate")
	public ResponseEntity<AuthDTO> login(@RequestBody AuthRequest request) {
		System.out.println("AuthController.login");
		AuthDTO response = authService.authenticate(request);
		System.out.println("AuthController.login: response: " + response);
		return ResponseEntity.ok(response);
	}
	
	@GetMapping("/logout")
	public void logout(HttpServletRequest request, HttpServletResponse response) {
		authService.logout(request, response);
	}
}
