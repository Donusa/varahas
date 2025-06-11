package varahas.main.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import varahas.main.entities.TnProduct;
import varahas.main.output.TiendaNubeApiOutput;
import varahas.main.services.TenantService;

@RestController
@RequestMapping("/api/tn")
public class TnController {

	@Autowired
	private TiendaNubeApiOutput tiendaNubeApiOutput;
	@Autowired
	private TenantService tenantService;
	
	@PostMapping("/set-token")
	public ResponseEntity<?> setToken(@RequestBody String token, @RequestParam String tenantName) {
		if (token == null || token.isEmpty()) {
			return ResponseEntity.badRequest().body("Token no puede ser nulo o vacío");
		}
		var tenant = this.tenantService.getTenantByName(tenantName);
		if (tenant == null) {
			return ResponseEntity.badRequest().body("Tenant no encontrado");
		}
		tenant.setTiendaNubeAccessToken(token);
		tenantService.save(tenant);
		return ResponseEntity.ok("Token guardado correctamente");
	}
	
	
	@PostMapping("/new-product")
	public ResponseEntity<?> newProduct(@RequestBody TnProduct productData, @RequestParam String tenantName) {
		if (productData == null) {
			return ResponseEntity.badRequest().body("Datos del producto no pueden ser nulos o vacíos");
		}
		var tenant = this.tenantService.getTenantByName(tenantName);
		if (tenant == null) {
			return ResponseEntity.badRequest().body("Tenant no encontrado");
		}
		var response = tiendaNubeApiOutput.createProduct(productData, tenant);
		return ResponseEntity.ok(response);
	}
	
	@GetMapping("/all")
	public ResponseEntity<?> getAllProducts(@RequestParam String tenantName) {
		var tenant = this.tenantService.getTenantByName(tenantName);
		if (tenant == null) {
			return ResponseEntity.badRequest().body("Tenant no encontrado");
		}
		var response = tiendaNubeApiOutput.getAllProductsForUser(tenant);
		return ResponseEntity.ok(response);
	}
	
	@GetMapping
	public ResponseEntity<?> getProduct(@RequestParam String tenantName, @RequestParam String id) {
		var tenant = this.tenantService.getTenantByName(tenantName);
		if (tenant == null) {
			return ResponseEntity.badRequest().body("Tenant no encontrado");
		}
		var response = tiendaNubeApiOutput.getItemById(id, tenant);
		return ResponseEntity.ok(response);
	}
	
	@PutMapping
	public ResponseEntity<?> updateProduct(@RequestBody TnProduct productData, @RequestParam String tenantName) {
		if (productData == null || productData.getId() == null) {
			return ResponseEntity.badRequest()
					.body("Datos del producto no pueden ser nulos o el ID debe estar presente");
		}
		var tenant = this.tenantService.getTenantByName(tenantName);
		if (tenant == null) {
			return ResponseEntity.badRequest().body("Tenant no encontrado");
		}
		var response = tiendaNubeApiOutput.updateProduct(productData, tenant);
		return ResponseEntity.ok(response);
	}
	
}
