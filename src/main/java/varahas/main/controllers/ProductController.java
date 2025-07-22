package varahas.main.controllers;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import varahas.main.dto.MlUpdateProductRequest;
import varahas.main.dto.ProductDTO;
import varahas.main.dto.VariationsDTO;
import varahas.main.entities.Product;
import varahas.main.entities.Variations;
import varahas.main.output.MercadoLibreApiOutput;
import varahas.main.services.AuthorizationService;
import varahas.main.services.ProductService;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final MercadoLibreApiOutput mercadoLibreApiOutput;

	@Autowired
	private ProductService productService;
	@Autowired
	private AuthorizationService authorization;


    ProductController(MercadoLibreApiOutput mercadoLibreApiOutput) {
        this.mercadoLibreApiOutput = mercadoLibreApiOutput;
    }
	

	@GetMapping("/all")
	public ResponseEntity<?> getProducts(@RequestParam String tennantName) {
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
	
	@PutMapping("/update")
	public ResponseEntity<?>updateProduct(@RequestParam String tennantName, @RequestBody Product product){
		MlUpdateProductRequest mlUpdateProductRequest = MlUpdateProductRequest.builder().variations(getVariations(product)).build();
		Boolean state = mercadoLibreApiOutput.stockUpdate(product.getMercadoLibreId(), tennantName,mlUpdateProductRequest);
		if(!state){
			return ResponseEntity.badRequest().body("Product failed to update");
		}
		return ResponseEntity.ok("Product updated");
	}
	
	public List<VariationsDTO> getVariations(Product product){
		List<VariationsDTO> variations = new ArrayList<VariationsDTO>();
		
		for(Variations variation:product.getVariations()){
			if(variation.getMeliId()!= null){
			VariationsDTO variationDto = VariationsDTO.builder()
					.id(variation.getMeliId())
					.available_quantity(variation.getStock())
					.build();
			variations.add(variationDto);
			}
		}
		
		return variations;
	}  
}
