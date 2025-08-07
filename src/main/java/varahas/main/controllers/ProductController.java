package varahas.main.controllers;

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

import varahas.main.dto.ProductDTO;
import varahas.main.entities.Product;
import varahas.main.output.MercadoLibreApiOutput;
import varahas.main.request.MlUpdateProductRequest;
import varahas.main.services.AuthorizationService;
import varahas.main.services.ProductService;
import varahas.main.utils.MlUtils;

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
		Product prod = Product.builder().name(product.getName())
				.description(product.getDescription()).price(product.getPrice()).stock(product.getStock())
				.mercadoLibreId(product.getMercadoLibreId()).isOnMercadoLibre(product.getIsOnMercadoLibre())
				.isOnTiendaNube(product.getIsOnTiendaNube()).tennantName(tennantName).tiendaNubeId(product.getTiendaNubeId()).build();
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
		return ResponseEntity.ok(productService.getProductById(id));
	}
	
	@PutMapping("/update")
	public ResponseEntity<?>updateProduct(@RequestParam String tennantName,@RequestBody Product product){
		Product p = productService.getProductById(product.getId());
		if(p == null){
			return ResponseEntity.badRequest().body("Product not found");
		}
		Product aux = p.builder()
				.id(product.getId())
				.name(product.getName())
				.description(product.getDescription())
				.price(product.getPrice())
				.stock(product.getStock())
				.mercadoLibreId(product.getMercadoLibreId() == null?null:product.getMercadoLibreId())
				.tiendaNubeId(product.getTiendaNubeId() == null?null:product.getTiendaNubeId())
				.tennantName(product.getTennantName())
				.isOnMercadoLibre(product.getIsOnMercadoLibre())
				.isOnTiendaNube(product.getIsOnTiendaNube())
				.variations((product.getVariations() == null || product.getVariations().isEmpty())?null:product.getVariations())
				.build();
		
		productService.saveProduct(aux);
		
		if(aux.getIsOnTiendaNube() == 1){
			System.out.println("Esta en Tienda Nube");
		}
		
		if(aux.getIsOnMercadoLibre() == 1){
			System.out.println("Esta en Mercado Libre");
		}
		
			
		return ResponseEntity.ok("Product updated");
	}
	
	
}
