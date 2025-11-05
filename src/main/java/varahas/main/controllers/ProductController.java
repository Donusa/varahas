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
import varahas.main.services.AuthorizationService;
import varahas.main.services.ProductService;
import varahas.main.services.VariationService;

@RestController
@RequestMapping("/api/products")
public class ProductController {

	@Autowired
	private VariationService variationService;
	@Autowired
	private ProductService productService;
	@Autowired
	private AuthorizationService authorization;
	@Autowired
	private StockUpdateQueueHandler stockUpdateQueueHandler;
    
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
        Long incomingId = product.getId();
        Long normalizedId = (incomingId == null || incomingId <= 0L) ? null : incomingId;
		Product prod = Product.builder()
                .id(normalizedId)
                .name(product.getName())
				.description(product.getDescription())
                .price(product.getPrice())
                .stock(product.getStock())
				.mercadoLibreId(product.getMercadoLibreId())
                .isOnMercadoLibre(product.getIsOnMercadoLibre())
				.isOnTiendaNube(product.getIsOnTiendaNube())
                .tennantName(tennantName)
                .build();
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
		
		System.out.println("🔎 Variaciones con cambio de stock: " + list);
		productService.saveProduct(aux);
		System.out.println("💾 Producto guardado antes de encolar eventos: " + aux.getId());
		
		// Crear mapa de stocks entrantes por variación para usar el valor correcto al encolar
		Map<Long, Integer> incomingStockById = new HashMap<>();
		if (product.getVariations() != null) {
			for (Variations vIn : product.getVariations()) {
				incomingStockById.put(vIn.getId(), vIn.getStock());
			}
		}
		
		for(Long variationId : list){
			Integer newStock = incomingStockById.get(variationId);
			if (newStock == null) {
				Variations var = variationService.getById(variationId);
				newStock = var != null ? var.getStock() : null;
			}
			if (newStock != null) {
				System.out.println("📥 Encolando evento LOCAL para variación " + variationId + ", nuevo stock=" + newStock);
				stockUpdateQueueHandler.enqueueEvent(variationId, newStock, SourceChannel.LOCAL);
			}
		}
		
		return ResponseEntity.ok("Product updated");
	}
	
	@DeleteMapping("/variation/remove")
	public ResponseEntity<?> removeProductVariation(@RequestParam String tennantName,
														@RequestParam Long variationId,
														@RequestParam String scope) {
		var authorized = authorization.isUserAuthorized(tennantName);
		if (!authorized) {
			return ResponseEntity.badRequest().body("User not authorized to access tennant");
		}
		try {
			Variations variation = variationService.getById(variationId);
			if (variation == null || variation.getProduct() == null || variation.getProduct().getTennantName() == null ||
					!variation.getProduct().getTennantName().equals(tennantName)) {
				return ResponseEntity.badRequest().body("Variation not found for tenant");
			}
			variationService.removeVariation(variationId, scope);
			return ResponseEntity.ok("Variation removal executed with scope: " + scope);
		} catch (IllegalArgumentException iae) {
			return ResponseEntity.badRequest().body(iae.getMessage());
		} catch (Exception e) {
			return ResponseEntity.internalServerError().body("Error removing variation");
		}
	}
	
}
