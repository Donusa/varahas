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
@RequestMapping("api/ml")
public class MlController {

	@Autowired
	private MercadoLibreApiOutput mercadoLibreApiOutput;
	
	@GetMapping("/trade-access-token")
	public ResponseEntity<?>tradeAcessToken(@RequestParam String code, @RequestParam Long tenantId){
		MlTokenResponse response = (MlTokenResponse) mercadoLibreApiOutput.tradeAccessToken(code,tenantId);
		return ResponseEntity.ok(response);
	}
	
	@GetMapping("/get-refresh-token")
	public ResponseEntity<?>getRefreshToken(@RequestParam Long tenantId){
		MlTokenResponse response = mercadoLibreApiOutput.getAccessToken(tenantId);
		return ResponseEntity.ok(response);
	}
	
	@GetMapping("/items")
	public ResponseEntity<?>getMlItems(@RequestParam String userId, @RequestParam Long tenantId){
		List<String> items = mercadoLibreApiOutput.getAllItemsForUser(userId, tenantId);
		return ResponseEntity.ok(items);
	}
	
	@GetMapping("/items/{itemId}")
	public ResponseEntity<?>getItemInfo(@PathVariable String itemId, @RequestParam Long tenantId){
		MeliItemDto item = mercadoLibreApiOutput.getItemData(itemId, tenantId);
		return ResponseEntity.ok(item);
	}
	
	@GetMapping("/stock/{meliId}")
	public ResponseEntity<?>getCurrentMELIStock(@PathVariable String meliId, @RequestParam Long tenantId){
		MlItemResponse response = mercadoLibreApiOutput.getCurrentMELIStock(meliId, tenantId);
		return ResponseEntity.ok(response);
	} 
	
	@PostMapping("/items")
	public ResponseEntity<?>postProduct(@RequestBody MlProductRequest request, @RequestParam Long tenantId){
		MeliItemDto item = mercadoLibreApiOutput.postProduct(request, tenantId);
		return ResponseEntity.ok(item);
	}
}
