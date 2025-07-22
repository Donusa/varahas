package varahas.main.controllers;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import varahas.main.configuration.ApplicationConfig;
import varahas.main.dao.MlauDao;
import varahas.main.dto.MeliItemDto;
import varahas.main.dto.MeliVariationDto;
import varahas.main.dto.StockUpdate;
import varahas.main.entities.Tenant;
import varahas.main.entities.Variations;
import varahas.main.output.MercadoLibreApiOutput;
import varahas.main.output.TiendaNubeApiOutput;
import varahas.main.services.ProductService;
import varahas.main.services.TenantService;
import varahas.main.services.VariationService;

@RestController
@RequestMapping("/webhooks")
public class WebhooksController {

    private final ApplicationConfig applicationConfig;


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
	@Autowired
	private ProductService productService;


    WebhooksController(ApplicationConfig applicationConfig) {
        this.applicationConfig = applicationConfig;
    }
	
	
	@PostMapping("/ml")
	public ResponseEntity<Void> recibirNotificacion(@RequestBody Map<String, Object> payload) {
		System.out.println("📦 Notificación recibida de Mercado Libre:");
		payload.forEach((k, v) -> System.out.println(k + ": " + v));

		String resource = (String) payload.get("resource");
		String userId = String.valueOf(payload.get("user_id"));
		MlauDao mlauDao = mercadoLibreApiOutput.findMlId(resource);
		String itemId = mlauDao.getMla();
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
	            String meliVariationId = meliVar.getUserProductId();
	            Integer meliStock = meliVar.getAvailableQuantity();

	            Optional<Variations> optionalVar = variationService.findByMlau(meliVariationId, true);

	            Variations variation = optionalVar.get();

	            variationService.updateStockFromWebhookMl(variation.getId(), meliStock);
	            
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
		    System.out.println("Fin");

		    try {
		        String storeId = String.valueOf(payload.get("store_id"));
		        String productId = String.valueOf(payload.get("id"));

		        Tenant tenant = tenantService.findByTnUserId(storeId);
		        if (tenant == null) {
		            System.out.println("🚫 No se encontró el tenant para el store_id: " + storeId);
		            return ResponseEntity.badRequest().build();
		        }
		      
		        var n = tiendaNubeApiOutput.getVariants(productId, tenant);
		        
		        var product = productService.findByTiendaNubeId(productId); 
		        
		        Map<String,Integer>trackingSet = new HashMap<>();
		        
		        
		        for(Map<String,Object> m : n){
		        	
		        		String inventory = String.valueOf(m.get("inventory_levels"));
		        		int beginIndex = inventory.indexOf("variant_id=")+11;
		        		int endIndex = inventory.indexOf(",",beginIndex);
		        		
		        		int beginIndex2 = inventory.indexOf("stock=")+6;
		        		int endIndex2 = inventory.indexOf("}",beginIndex2);
		        		
		        		String variantId = inventory.substring(
		        				beginIndex,endIndex);
		        		
		        		String stock = inventory.substring(
		        				beginIndex2,endIndex2);
		        		
		        		trackingSet.put(variantId, Integer.valueOf(stock));
		        }
		        
		        for(Variations v : product.getVariations()){
		        	
		        	if(trackingSet.containsKey(v.getTnId())){
		        		int remoteStock = trackingSet.get(v.getTnId());
		        		int localStock = v.getStock();
		        		
		        		if(localStock != remoteStock){
		        			v.setStock(remoteStock);
		        		}
		        	}
		        }
		        
		        	
		        
		        
		        /*
		        variationService.updateStockFromWebhook(variation.getId(), stockDesdeTn);

		        StockUpdate stockUpdate = variationService.buildStockUpdate(variation.getProduct());
		        messagingTemplate.convertAndSend("/topic/stock", stockUpdate);

		        System.out.println("✅ Notificación de Tienda Nube procesada correctamente.");
*/
		        return ResponseEntity.ok().build();
		       

		    } catch (Exception e) {
		        System.out.println("🚫 Error al procesar la notificación: " + e.getMessage());
		        return ResponseEntity.badRequest().build();
		    }
	}
	
	

}