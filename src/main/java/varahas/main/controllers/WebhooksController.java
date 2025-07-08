package varahas.main.controllers;

import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import varahas.main.dto.MeliItemDto;
import varahas.main.dto.MeliVariationDto;
import varahas.main.dto.StockUpdate;
import varahas.main.entities.Tenant;
import varahas.main.entities.Variations;
import varahas.main.output.MercadoLibreApiOutput;
import varahas.main.output.TiendaNubeApiOutput;
import varahas.main.services.TenantService;
import varahas.main.services.VariationService;

@RestController
@RequestMapping("/webhooks")
public class WebhooksController {

	@Autowired
	private TenantService tenantService;
	@Autowired
	private MercadoLibreApiOutput mercadoLibreApiOutput;
	@Autowired
	private TiendaNubeApiOutput tiendaNubeApiOutput;
	@Autowired
	private SimpMessagingTemplate messagingTemplate;
	@Autowired
	private VariationService variationService;

	@PostMapping("/ml")
	public ResponseEntity<Void> recibirNotificacion(@RequestBody Map<String, Object> payload) {
		System.out.println("📦 Notificación recibida de Mercado Libre:");
		payload.forEach((k, v) -> System.out.println(k + ": " + v));

		String resource = (String) payload.get("resource");
		String userId = String.valueOf(payload.get("user_id"));
		String itemId = resource.replace("/items/", "");
		try {

			Tenant tenant = tenantService.findByMlUserId(userId);
			if (tenant == null) {
				System.out.println("🚫 No se encontró el tenant para el user_id: " + userId);
				return ResponseEntity.badRequest().build();
			}
			MeliItemDto item = mercadoLibreApiOutput.getItemData(itemId, tenant.getName());
	        if (item == null || item.getVariations() == null || item.getVariations().isEmpty()) {
	            System.out.println("🚫 El item no tiene variaciones: " + itemId);
	            return ResponseEntity.badRequest().build();
	        }

	        for (MeliVariationDto meliVar : item.getVariations()) {
	            Long meliVariationId = meliVar.getId();
	            Integer meliStock = meliVar.getAvailableQuantity();

	            Optional<Variations> optionalVar = variationService.getByMeliId(String.valueOf(meliVariationId));
	            if (optionalVar.isEmpty()) {
	                System.out.println("⚠️ No se encontró la variation local para meliId: " + meliVariationId);
	                continue;
	            }

	            Variations variation = optionalVar.get();

	            variationService.updateStockFromWebhook(variation.getId(), meliStock);

	            StockUpdate stockUpdate = variationService.buildStockUpdate(variation.getProduct());
	            messagingTemplate.convertAndSend("/topic/stock", stockUpdate);
	        }
		} catch (Exception e) {
			System.out.println("🚫 Error al procesar la notificación: " + e.getMessage());
			System.out.println("TenantName o itemId no encontrados");
			return ResponseEntity.badRequest().build();
		}
		return ResponseEntity.ok().build();
	}

	@PostMapping("/tn")
	public ResponseEntity<Void> recibirNotificacionTN(@RequestBody Map<String, Object> payload) {
		 System.out.println("📦 Notificación recibida de Tienda Nube:");
		    payload.forEach((k, v) -> System.out.println(k + ": " + v));

		    try {
		        String storeId = String.valueOf(payload.get("store_id"));
		        String variationId = String.valueOf(payload.get("variation_id"));

		        Tenant tenant = tenantService.findByTnUserId(storeId);
		        if (tenant == null) {
		            System.out.println("🚫 No se encontró el tenant para el store_id: " + storeId);
		            return ResponseEntity.badRequest().build();
		        }

		        Optional<Variations> optionalVariation = variationService.getByTnId(variationId);
		        if (optionalVariation.isEmpty()) {
		            System.out.println("⚠️ No se encontró la variation local para tnId: " + variationId);
		            return ResponseEntity.badRequest().build();
		        }

		        Variations variation = optionalVariation.get();

		        Integer stockDesdeTn = tiendaNubeApiOutput.getVariationStock(variationId, tenant);
		        if (stockDesdeTn == null) {
		            System.out.println("⚠️ No se pudo obtener el stock desde Tienda Nube");
		            return ResponseEntity.badRequest().build();
		        }

		        variationService.updateStockFromWebhook(variation.getId(), stockDesdeTn);

		        StockUpdate stockUpdate = variationService.buildStockUpdate(variation.getProduct());
		        messagingTemplate.convertAndSend("/topic/stock", stockUpdate);

		        System.out.println("✅ Notificación de Tienda Nube procesada correctamente.");

		        return ResponseEntity.ok().build();

		    } catch (Exception e) {
		        System.out.println("🚫 Error al procesar la notificación: " + e.getMessage());
		        return ResponseEntity.badRequest().build();
		    }
	}

}