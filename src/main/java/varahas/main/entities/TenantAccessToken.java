package varahas.main.entities;


import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TenantAccessToken {
	@Id
	@GeneratedValue
	private Long id;

	@Column(nullable = false)
	private String accessToken;
	
	@Column(nullable = false)
	private String refreshToken;
	
	@Column(nullable = false)
	private LocalDateTime expirationDate;
	
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "tenant_id",nullable = false)
	private Tenant tenant;
}
