package varahas.main.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import varahas.main.dto.MeliItemDto;
import varahas.main.dto.MlItemResponse;
import varahas.main.dto.MlProductRequest;
import varahas.main.dto.MlTokenResponse;
import varahas.main.output.MercadoLibreApiOutput;

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
	public Boolean validateConnection(@RequestParam String tenantName){
		return(mercadoLibreApiOutput.validateAcessToken(tenantName));
	}
	
	@GetMapping("/items")
	public ResponseEntity<?>getItems(@RequestParam String tenantName){
		List<String> items = mercadoLibreApiOutput.getAllItemsForUser(tenantName);
		return ResponseEntity.ok(items);
	}
	
	@GetMapping("/items/{itemId}")
	public ResponseEntity<?>getItemInfo(@PathVariable String itemId, @RequestParam String tenantName){
		MeliItemDto item = mercadoLibreApiOutput.getItemData(itemId, tenantName);
		return ResponseEntity.ok(item);
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
}
