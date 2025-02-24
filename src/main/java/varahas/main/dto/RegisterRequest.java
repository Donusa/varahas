package varahas.main.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequest {

	private String email;
	private String password;
	private String role;
	private String name;
	private String phone;
	private String username;
	private String tenantName;
}