package varahas.main.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import varahas.main.dto.ProductDTO;
import varahas.main.entities.Product;
import varahas.main.services.AuthorizationService;
import varahas.main.services.ProductService;

@RestController
@RequestMapping("/api/products")
public class ProductController {

	@Autowired
	private ProductService productService;
	@Autowired
	private AuthorizationService authorization;
	

	@GetMapping("/all")
	public ResponseEntity<?> getProducts(@RequestParam String tennantName) {
		System.out.println("ProductController.getProducts");
		var authorized = authorization.isUserAuthorized(tennantName);
		if (!authorized) {
			return ResponseEntity.badRequest().body("User not authorized to access tennant");
		}
		var prods = productService.getProductsByTennantName(tennantName);
		return ResponseEntity.ok(ProductDTO.fromList(prods));
	}

	@PostMapping("/save")
	public ResponseEntity<?> saveProduct(@RequestParam String tennantName, @RequestBody ProductDTO product) {
		var authorized = authorization.isUserAuthorized(tennantName);
		if (!authorized) {
            return ResponseEntity.badRequest().body("User not authorized to access tennant");
		}
		Product prod = Product.builder().id(product.getId()).name(product.getName())
				.description(product.getDescription()).price(product.getPrice()).stock(product.getStock())
				.mercadoLibreId(product.getMercadoLibreId()).isOnMercadoLibre(product.getIsOnMercadoLibre())
				.isOnTiendaNube(product.getIsOnTiendaNube()).tennantName(tennantName).build();
			productService.saveProduct(prod);
			return ResponseEntity.ok("Product saved");
	}

	@DeleteMapping("/delete")
	public ResponseEntity<?> deleteProduct(@RequestParam String tennantName, @RequestParam Long id) {
		var authorized = authorization.isUserAuthorized(tennantName);
		if (!authorized) {
			return ResponseEntity.badRequest().body("User not authorized to access tennant");
		}
		productService.deleteProduct(id);
		return ResponseEntity.ok("Product deleted");
	}

	@GetMapping("/get")
	public ResponseEntity<?> getProduct(@RequestParam String tennantName, @RequestParam Long id) {
		var authorized = authorization.isUserAuthorized(tennantName);
		if (!authorized) {
            return ResponseEntity.badRequest().body("User not authorized to access tennant");
		}
		return ResponseEntity.ok(productService.getProduct(id));
	}
}
