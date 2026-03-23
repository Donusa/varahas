package varahas.main.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import varahas.main.entities.SalesHistory;
import varahas.main.entities.Tenant;

@Repository
public interface SalesHistoryRepository extends JpaRepository<SalesHistory, Long> {

	Page<SalesHistory> findByTenant(Tenant tenant, Pageable pageable);
}
