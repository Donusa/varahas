package varahas.main.controllers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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
import varahas.main.entities.Variations;
import varahas.main.enums.SourceChannel;
import varahas.main.queue.StockUpdateQueueHandler;
import varahas.main.repositories.VariationRepository;
import varahas.main.services.AuthorizationService;
import varahas.main.services.ProductService;
import varahas.main.services.VariationService;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final VariationRepository variationRepository;

    private final VariationService variationService;


	@Autowired
	private ProductService productService;
	@Autowired
	private AuthorizationService authorization;
	@Autowired
	private StockUpdateQueueHandler stockUpdateQueueHandler;


    ProductController(VariationService variationService, VariationRepository variationRepository) {
        this.variationService = variationService;
        this.variationRepository = variationRepository;
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
		Product aux = Product.builder()
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
		
		Map<Long,Variations> originalVariationsMap = p.getVariations() != null ? 
			p.getVariations().stream().collect(Collectors.toMap(Variations::getId,v->v)) : 
			new HashMap<>();
		
		List<Long>list = product.getVariations() != null ? 
			product.getVariations().stream().filter(
				v->{
					Variations originalVariation = originalVariationsMap.get(v.getId());
					return originalVariation != null && !Objects.equals(v.getStock(), originalVariation.getStock());
				}
			).map(Variations::getId).toList() : new ArrayList<>();
		
		for(Long variationId : list){
			System.out.println("bucleeee");
			stockUpdateQueueHandler.enqueueEvent(variationId,variationService.getById(variationId).getStock(),SourceChannel.LOCAL);
		}
		
		productService.saveProduct(aux);
		
			
		return ResponseEntity.ok("Product updated");
	}
	
	
}
