package varahas.main.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import varahas.main.entities.Variations;

@Repository
public interface VariationRepository extends JpaRepository<Variations, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Variations v WHERE v.id = :id")
    Optional<Variations> findByIdForUpdate(@Param("id") Long id);

    Optional<Variations> findByMeliId(String meliId);

    Optional<Variations> findByTnId(String tnId);
}
