package varahas.main.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import varahas.main.entities.TenantAccessToken;

@Repository
public interface TenantAccessTokenRepository extends JpaRepository<TenantAccessToken, Long>{
	
	Optional<TenantAccessToken>findByTenantId(Long tenantId);
	
}
