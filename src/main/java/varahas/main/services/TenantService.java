package varahas.main.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import varahas.main.dto.TnAuthDto;
import varahas.main.entities.Tenant;
import varahas.main.repositories.TenantRepository;

@Service
public class TenantService {
	
	@Autowired
	private TenantRepository tenantRepository;
	
	public Tenant getTenantById(Long id){
		 return tenantRepository.findById(id).orElseThrow(
				()->new RuntimeException("Id not found"));
	}
	
	public Tenant getTenantByName(String tenantName){
		return tenantRepository.findByName(tenantName).orElseThrow(
				()->new RuntimeException("Name not found"));
	}
	public Tenant save(Tenant tenant){
		return tenantRepository.save(tenant);
	}

	public Tenant findByMlUserId(String userId) {
		return this.tenantRepository.findByMlUserId(userId)
				.orElseThrow(() -> new RuntimeException("Tenant not found for user_id: " + userId));
	}

	public void setTnData(Tenant tenant, TnAuthDto authData) {
		if (tenant == null || authData == null) {
			throw new IllegalArgumentException("Tenant or authData cannot be null");
		}
		tenant.setTiendaNubeAccessToken(authData.getAccessToken());
		tenantRepository.save(tenant);
		
	}

}
