package varahas.main.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import varahas.main.entities.Tenant;
import varahas.main.services.MlOrderService;
import varahas.main.services.TenantService;

@RestController
@RequestMapping("/webhooks")
public class WebhooksController {
	
	@Autowired 
	private MlOrderService mlOrderService;
	@Autowired
	private TenantService TenantService;
	
	@PostMapping("/mercado-libre")
	public ResponseEntity<?> receiveMercadoLibreWebhook(@RequestBody JsonNode payload){
		if (!"orders_v2".equals(payload.get("topic").asText())) {
            return ResponseEntity.ok().build();
        }
		String resource = payload.get("resource").asText();
		Long orderId = Long.valueOf(resource.replaceAll("\\D+", ""));
		String userId = payload.get("user_id").asText();
		Tenant tenant = TenantService.findByMlUserId(userId);

        mlOrderService.handleNewOrder(orderId, tenant);
		return ResponseEntity.ok().build();
	}
	

}
