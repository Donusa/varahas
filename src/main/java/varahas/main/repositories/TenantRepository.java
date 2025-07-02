package varahas.main.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import varahas.main.entities.Tenant;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {
    
    Optional<Tenant> findByName(String name);
    @NonNull
    Optional<Tenant> findById(@NonNull Long id);
    Optional<Tenant> findByMlUserId(String mlUserId);
    Optional<Tenant> findByTnUserId(String tnUserId);

}
