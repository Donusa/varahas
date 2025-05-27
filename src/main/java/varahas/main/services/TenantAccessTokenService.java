package varahas.main.services;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import varahas.main.entities.TenantAccessToken;
import varahas.main.repositories.TenantAccessTokenRepository;

@Service
public class TenantAccessTokenService {

	@Autowired
	private TenantAccessTokenRepository tenantAcessTokenRepository;
	@Autowired
	private TenantService tenantService;
	
	public TenantAccessToken getAccessTokenByTenantId(Long tenantId) {
		return tenantAcessTokenRepository.findByTenantId(tenantId).orElseThrow(
				()-> new RuntimeException("TenantId not found"));
	}
	
	public boolean isRefreshTokenExpired(Long tenantId) {
		TenantAccessToken accessToken = this.getAccessTokenByTenantId(tenantId);
		return accessToken.getExpirationDate().isBefore(LocalDateTime.now());
	}
	
	public TenantAccessToken saveNew(Long tenantId){
		return tenantAcessTokenRepository.save(TenantAccessToken
				.builder()
				.tenant(tenantService.getTenantById(tenantId))
				.accessToken("")
				.refreshToken("")
				.expirationDate(LocalDateTime.now().plusHours(6))
				.build());
	}
	
	public TenantAccessToken save(TenantAccessToken tenantAcessToken){
		return tenantAcessTokenRepository.save(tenantAcessToken);
	}
}
