package varahas.main.controllers;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import varahas.main.dto.MeliItemDto;
import varahas.main.dto.StockUpdate;
import varahas.main.entities.Product;
import varahas.main.entities.Tenant;
import varahas.main.output.MercadoLibreApiOutput;
import varahas.main.services.ProductService;
import varahas.main.services.TenantService;

@RestController
@RequestMapping("/webhooks")
public class WebhooksController {

	@Autowired
	private ProductService productService;
	@Autowired
	private TenantService tenantService;
	@Autowired
	private MercadoLibreApiOutput mercadoLibreApiOutput;
	@Autowired
	private SimpMessagingTemplate messagingTemplate;

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

			Product product = productService.getProductByMercadoLibreId(itemId);
			if (product == null) {
				System.out.println("🚫 No se encontró el producto para el item_id: " + itemId);
				return ResponseEntity.badRequest().build();
			}

			MeliItemDto item = mercadoLibreApiOutput.getItemData(itemId, tenant.getName());
			if (item == null) {
				System.out.println(
						"🚫 No se pudo obtener los datos del item desde Mercado Libre para el item_id: " + itemId);
				return ResponseEntity.badRequest().build();
			}
			// cambiar por calculo apropiado
			messagingTemplate.convertAndSend("/topic/stock", new StockUpdate(product.getId(), 1));
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

		String storeId = String.valueOf(payload.get("store_id"));
		String productId = String.valueOf(payload.get("product_id"));

		Tenant tenant = tenantService.findByTnUserId(storeId);
		if (tenant == null) {
			System.out.println("🚫 No se encontró el tenant para el store_id: " + storeId);
			return ResponseEntity.badRequest().build();
		}

		System.out.println("✅ Notificación de Tienda Nube recibida correctamente para el producto: " + productId);

		return ResponseEntity.ok().build();
	}

}