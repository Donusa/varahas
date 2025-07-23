package varahas.main.controllers;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import varahas.main.dto.MeliItemDto;
import varahas.main.output.MercadoLibreApiOutput;
import varahas.main.request.MlProductRequest;
import varahas.main.response.MlItemResponse;
import varahas.main.response.MlTokenResponse;

@RestController
@RequestMapping("/api/ml")
public class MlController {

	@Autowired
	private MercadoLibreApiOutput mercadoLibreApiOutput;
	
	@GetMapping("/trade-access-token")
	public ResponseEntity<?>tradeAcessToken(@RequestParam String code, @RequestParam String tenantName){
		MlTokenResponse response = (MlTokenResponse) mercadoLibreApiOutput.tradeAccessToken(code,tenantName);
		return ResponseEntity.ok((response!= null)?"Token de acceso intercambiado correctamente"
				: "El token de acceso no pudo ser intercambiado");
	}
	
	@GetMapping("/validate")
	public ResponseEntity<?> validateConnection(@RequestParam String tenantName) {
	    try {
	        Boolean isValid = mercadoLibreApiOutput.validateAcessToken(tenantName);
	        if (!isValid) {
	            return ResponseEntity
	                    .status(HttpStatus.UNAUTHORIZED)
	                    .body("Token inválido o expirado");
	        }
	        return ResponseEntity.ok("Token válido encontrado");

	    } catch (RuntimeException ex) {
	        return ResponseEntity
	                .status(HttpStatus.NOT_FOUND)
	                .body(ex.getMessage());
	    }
	}
	
	@GetMapping("/items")
	public ResponseEntity<?>getItems(@RequestParam String tenantName){
		List<String> items = mercadoLibreApiOutput.getAllItemsForUser(tenantName);
		var response = items.stream().map(i->mercadoLibreApiOutput.getItemData(i, tenantName)).collect(Collectors.toList());
		return ResponseEntity.ok(response);
	}
	
	@GetMapping("/stock/{meliId}")
	public ResponseEntity<?>getCurrentMELIStock(@PathVariable String meliId, @RequestParam String tenantName){
		MlItemResponse response = mercadoLibreApiOutput.getCurrentMELIStock(meliId, tenantName);
		return ResponseEntity.ok(response);
	} 
	
	@PostMapping("/items")
	public ResponseEntity<?>postProduct(@RequestBody MlProductRequest request, @RequestParam String tenantName){
		MeliItemDto item = mercadoLibreApiOutput.postProduct(request, tenantName);
		return ResponseEntity.ok(item);
	}
	
	@GetMapping("/categories")
	public ResponseEntity<?> getCategories(@RequestParam String tenantName, @RequestParam String siteId) {
		return ResponseEntity.ok(mercadoLibreApiOutput.getCategories(siteId, tenantName));
	}
	
	@GetMapping("/attributes")
	public ResponseEntity<?> getAttributes(@RequestParam String tenantName, @RequestParam String categoryId) {
		return ResponseEntity.ok(mercadoLibreApiOutput.getCategoryAttributes(tenantName, categoryId));
	}
	
}
