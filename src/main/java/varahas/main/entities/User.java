package varahas.main.entities;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import varahas.main.enums.Roles;
import varahas.main.enums.Status;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User implements UserDetails {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private Long id;
	@Column(nullable = false)
	private String name;
	@Column(nullable = false, unique = true)
	private String email;
	private String phone;
	@Column(nullable = false, unique = true)
	private String username;
	@Column(nullable = false)
	private String password;
	private Roles roles;
	@Enumerated(EnumType.STRING)
	@Builder.Default
	private Status status = Status.ACTIVE;
	@ManyToOne
	@JoinColumn(name = "tenant_id", nullable = false)
	private Tenant tenant;
	@Column(name = "reset_token")
	private String resetToken;
	@Column(name = "reset_token_expiry")
	private LocalDateTime resetTokenExpiry;
	@OneToMany(mappedBy = "user",cascade = CascadeType.REMOVE)
	@JsonIgnore
	private List<Token> tokens;


	@Override
	public String getUsername() {
		return this.username;
	}

	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public String getPassword() {
		return this.password;
	}
	
	@Override
	public String toString() {
		return "";
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return Collections.singleton(new SimpleGrantedAuthority(roles.toString()));
	}

}
