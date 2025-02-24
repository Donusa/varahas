package varahas.main.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import varahas.main.entities.Token;

@Repository
public interface TokenRepository extends JpaRepository<Token, Long> {
	
	@Query("select t from Token t join t.user u where u.id = :id and (t.expired = false or t.revoked = false)")
	List<Token> findAllValidTokenByUser(@Param("id") Long id);
	
	Optional<Token> findByToken(String token);
}
