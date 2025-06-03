package varahas.main.services;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import varahas.main.entities.TenantAccessToken;
import varahas.main.repositories.TenantAccessTokenRepository;

@Service
public class TenantAccessTokenService {

	@Autowired
	private TenantAccessTokenRepository tenantAccessTokenRepository;
	@Autowired
	private TenantService tenantService;
	
	public TenantAccessToken getAccessTokenByTenantId(Long tenantId) {
		return tenantAccessTokenRepository.findByTenantId(tenantId).orElseThrow(
				()-> new RuntimeException("TenantId not found"));
	}
	
	public TenantAccessToken getAccessTokenByTenantName(String tenantName){
		return tenantAccessTokenRepository.findByTenantName(tenantName).orElseThrow(
				()-> new RuntimeException("Tenant Name not found"));
	}
	
	public boolean isRefreshTokenExpired(Long tenantId) {
		TenantAccessToken accessToken = this.getAccessTokenByTenantId(tenantId);
		return accessToken.getExpirationDate().isBefore(LocalDateTime.now());
	}
	
	public TenantAccessToken saveNew(String tenantName){
		return tenantAccessTokenRepository.save(TenantAccessToken
				.builder()
				.tenant(tenantService.getTenantByName(tenantName))
				.accessToken("")
				.refreshToken("")
				.expirationDate(LocalDateTime.now().plusHours(6))
				.build());
	}
	
	public TenantAccessToken save(TenantAccessToken tenantAcessToken){
		return tenantAccessTokenRepository.save(tenantAcessToken);
	}
}
