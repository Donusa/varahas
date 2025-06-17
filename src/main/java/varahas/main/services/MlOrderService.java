package varahas.main.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;

import varahas.main.entities.Tenant;
import varahas.main.notifications.StockNotificationService;

@Service
public class MlOrderService {

	@Autowired
	private ProductService productService;
    @Autowired 
    private StockNotificationService notifier;
    
    private final RestTemplate restTemplate;
	
	MlOrderService(RestTemplate restTemplate) {
		this.restTemplate = restTemplate;
	}
    
	public void handleNewOrder(Long orderId, Tenant tenant) {
		String token = tenant.getMlAccessToken();
	    if (token == null) throw new RuntimeException("El tenant no tiene token");

	    String url = "https://api.mercadolibre.com/orders/" + orderId;
	    HttpHeaders headers = new HttpHeaders();
	    headers.setBearerAuth(token);

	    ResponseEntity<JsonNode> response = restTemplate
	            .exchange(url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);

	    JsonNode order = response.getBody();
		if (order == null || !order.has("order_items")) {
			throw new RuntimeException("No se encontraron items en la orden: " + orderId);
		}
	    order.get("order_items").forEach(itemNode -> {
	        String itemId = itemNode.get("item").get("id").asText();
	        int qty = itemNode.get("quantity").asInt();
	        var ent = productService.getProductByMercadoLibreId(itemId);
	        ent.getMeliItem().setAvailableQuantity(ent.getMeliItem().getAvailableQuantity() - qty);
	        ent.setStock(ent.getStock() - qty);
	        productService.saveProduct(ent);
	        notifier.sendUpdate(itemId, ent.getStock());
	    });
	}

}
