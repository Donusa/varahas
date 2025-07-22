package varahas.main.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import varahas.main.entities.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>{
	
	@NonNull
	Optional<Product> findById(@NonNull Long id);
	Optional<Product> findByName(@NonNull String name);
	Optional<Product> findByMercadoLibreId(@NonNull String mercadoLibreId);
	Optional<Product> findByTiendaNubeId(@NonNull String tiendaNubeId);
	@NonNull
	List<Product> findAll();
	Optional<List<Product>> findAllByTennantName(@NonNull String tennantName);
}
