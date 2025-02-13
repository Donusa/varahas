package varahas.main.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import varahas.main.entities.Tenant;

@Repository
public interface TenantRespository extends JpaRepository<Tenant,String>{

	Optional<Tenant> findByTenantId(String tenantId);
}
