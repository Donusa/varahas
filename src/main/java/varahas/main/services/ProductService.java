package varahas.main.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import varahas.main.entities.Product;
import varahas.main.exceptions.IllegalTennantAccessException;
import varahas.main.repositories.ProductRepository;

@Service
public class ProductService {

	@Autowired
	private ProductRepository productRepository;	
	
	public void deleteProduct(Long id) {
		productRepository.deleteById(id);
	}
	
	public Product getProductById(Long id) {
		return productRepository.findById(id).orElseThrow(
				() -> new IllegalArgumentException("Product not found"));
	}
	
	public Product getProductByName(String name) {
		return productRepository.findByName(name).orElseThrow(
				() -> new IllegalArgumentException("Product not found"));
	}
	
	public Product getProductByMercadoLibreId(String mercadoLibreId) {
		return productRepository.findByMercadoLibreId(mercadoLibreId).orElseThrow(
				() -> new IllegalArgumentException("Product not found"));
	}
	
	public void saveProduct(Product product) {
		productRepository.save(product);
	}
	
	public List<Product> getProducts() {
		return productRepository.findAll();
	}
	
	public List<Product> getProductsByTennantName(String tennantName) {
		return productRepository.findAllByTennantName(tennantName)
				.orElseThrow(() -> new IllegalArgumentException("Product not found"));
	}
	public Product findByTiendaNubeId(String tiendaNubeId){
		return productRepository.findByTiendaNubeId(tiendaNubeId).orElseThrow(()-> new RuntimeException("Product not found"));
	}
	
	public void isProductFromTennant(Long productId, String tennantName) {
        Product product = productRepository.findById(productId).orElseThrow(
                () -> new IllegalArgumentException("Product not found"));
        if(!product.getTennantName().equals(tennantName)) {
        	  throw new IllegalTennantAccessException("Product does not belong to tennant");
        }
	}
}
