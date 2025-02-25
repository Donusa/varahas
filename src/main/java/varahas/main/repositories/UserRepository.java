package varahas.main.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import varahas.main.entities.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByUsername(String username);
	Optional<User> findByPhone(String phone);
	Optional<User> findByEmail(String email);
	Optional<User> findByResetToken(String resetToken);

}
