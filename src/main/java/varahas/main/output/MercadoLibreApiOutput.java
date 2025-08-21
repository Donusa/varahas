package varahas.main.output;

import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import varahas.main.dto.AttributeResponse;
import varahas.main.dto.CategoryResponse;
import varahas.main.dto.MeliItemDto;
import varahas.main.dto.VariationsDTO;
import varahas.main.entities.Product;
import varahas.main.entities.Tenant;
import varahas.main.request.MlProductRequest;
import varahas.main.request.MlUpdateProductRequest;
import varahas.main.response.MlItemResponse;
import varahas.main.response.MlTokenResponse;
import varahas.main.response.MlUserItemsResponse;
import varahas.main.services.TenantService;
import varahas.main.services.TokenSchedulerService;
import varahas.main.utils.MlUtils;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentLinkedQueue;
import lombok.Data;
import lombok.AllArgsConstructor;

@Service
public class MercadoLibreApiOutput {

	private final ConcurrentHashMap<String, Object> itemLocks = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, ConcurrentLinkedQueue<PendingUpdate>> pendingUpdates = new ConcurrentHashMap<>();
	private final ScheduledExecutorService coalescingExecutor = Executors.newScheduledThreadPool(2);

	@Data
	@AllArgsConstructor
	private static class PendingUpdate {
	    private String itemId;
	    private String tenantName;
	    private MlUpdateProductRequest request;
	}

	private final String MELI_BASE_URL = "https://api.mercadolibre.com";
	
	@Value("${mercadolibre.redirect.uri}")
	private String REDIRECT_URI;
	
	@Value("${mercadolibre.client.id}")
	private String clientId;

	@Value("${mercadolibre.client.secret}")
	private String clientSecret;

	@Autowired
	private RestTemplate restTemplate;

	@Autowired
	private TenantService tenantService;

	@Autowired
	private TokenSchedulerService schedulerService;

	public Boolean validateAcessToken(String tenantName) {
		Tenant tenant = tenantService.getTenantByName(tenantName);
		Date expiration = tenant.getMlAccessTokenExpirationDate();
		if (expiration == null || expiration.before(new Date())) {
			throw new RuntimeException("Token de acceso inválido o expirado");
		}
		
		return true;
	}

	public MlTokenResponse getAccessToken(String tenantName) {

		Tenant tenant;
		try {
			tenant = tenantService.getTenantByName(tenantName);
		} catch (Exception e) {
			throw new RuntimeException("Tenant no encontrado: " + tenantName);
		}
		
		String refreshToken = tenant.getMlRefreshToken();
		if (refreshToken == null || refreshToken.isEmpty()) {
			throw new RuntimeException("Refresh token no encontrado para el tenant: " + tenantName);
		}
		String url =  MELI_BASE_URL+"/oauth/token";

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

		MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
		map.add("grant_type", "refresh_token");
		map.add("client_id", clientId);
		map.add("client_secret", clientSecret);
		map.add("refresh_token", refreshToken);

		HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

		try {
			ResponseEntity<MlTokenResponse> response = restTemplate.postForEntity(url, request, MlTokenResponse.class);

			MlTokenResponse tokenResponse = response.getBody();
			if (tokenResponse != null) {
				tenant.setMlAccessToken(tokenResponse.access_token);
				tenant.setMlRefreshToken(tokenResponse.refresh_token);
				tenant.setMlAccessTokenExpirationDate(
					    new Date(System.currentTimeMillis() + 3 * 60 * 60 * 1000L));
				tenantService.save(tenant);
				return tokenResponse;
			} else {
				System.out.println("Error: Token response is null");
				return new MlTokenResponse();
			}
		} catch (Exception e) {
			System.out.println("Error al obtener refresh token de Mercado Libre: " + e.getMessage());
			return null;
		}
	}

	public Object tradeAccessToken(String code, String tenantName) {
		Tenant tenant;
		try {
			tenant = tenantService.getTenantByName(tenantName);
		} catch (Exception e) {
			throw new RuntimeException("Tenant no encontrado: " + tenantName);
		}
		String url =  MELI_BASE_URL+"/oauth/token";

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

		MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
		map.add("grant_type", "authorization_code");
		map.add("client_id", clientId);
		map.add("client_secret", clientSecret);
		map.add("code", code);
		map.add("redirect_uri", REDIRECT_URI);
		HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

		try {
			ResponseEntity<MlTokenResponse> response = restTemplate.postForEntity(url, request, MlTokenResponse.class);

			MlTokenResponse tokenResponse = response.getBody();
			if (tokenResponse != null) {
				tenant = tenantService.getTenantByName(tenantName);
				tenant.setMlAccessToken(tokenResponse.access_token);
				tenant.setMlRefreshToken(tokenResponse.refresh_token);
				tenant.setMlUserId(tokenResponse.user_id);
				System.out.println("New expiration date: " + new Date(System.currentTimeMillis() + 3 * 60 * 60 * 1000L));
				tenant.setMlAccessTokenExpirationDate(
					    new Date(System.currentTimeMillis() + 3 * 60 * 60 * 1000L));
				tenantService.save(tenant);

	            schedulerService.programarRenovacionTokenMl(tenant);
				return tokenResponse;
			} else {
				System.out.println("Error: Token response is null");
				return new MlTokenResponse();
			}
		} catch (Exception e) {
			System.out.println("Error al obtener token de Mercado Libre: " + e.getMessage());
			MlTokenResponse errorResponse = new MlTokenResponse();
			errorResponse.access_token = "";
			errorResponse.refresh_token = "";
			return errorResponse;
		}
	}

	public List<String> getAllItemsForUser(String tenantName) {
		Tenant tenant = tenantService.getTenantByName(tenantName);
		String url =  MELI_BASE_URL+"/users/" + tenant.getMlUserId() + "/items/search";

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(tenant.getMlAccessToken());

		HttpEntity<String> entity = new HttpEntity<>(headers);

		ResponseEntity<MlUserItemsResponse> response = restTemplate.exchange(url, HttpMethod.GET, entity,
				MlUserItemsResponse.class);

		MlUserItemsResponse itemsResponse = response.getBody();
		return itemsResponse != null ? itemsResponse.getResults() : null;
	}

	public MeliItemDto getItemData(String itemId, String tenantName) {
		Tenant tenant = tenantService.getTenantByName(tenantName);
		String url =  MELI_BASE_URL+"/items/" + itemId;
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(tenant.getMlAccessToken());

		HttpEntity<String> entity = new HttpEntity<>(headers);

		ResponseEntity<MeliItemDto> response = restTemplate.exchange(url, HttpMethod.GET, entity, MeliItemDto.class);

		return response.getBody();
	}

	public Integer getAvailableQuantity(String meliId, String tenantName) {
		MlItemResponse item = getCurrentMELIStock(meliId, tenantName);
		return item != null ? item.getAvailable_quantity() : null;
	}

	public MlItemResponse getCurrentMELIStock(String meliId, String tenantName) {
		Tenant tenant = tenantService.getTenantByName(tenantName);
		String url =  MELI_BASE_URL+"/items/" + meliId;

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(tenant.getMlAccessToken());

		HttpEntity<String> entity = new HttpEntity<>(headers);

		ResponseEntity<MlItemResponse> response = restTemplate.exchange(url, HttpMethod.GET, entity,
				MlItemResponse.class);

		return response.getBody();
	}

	public MeliItemDto postProduct(MlProductRequest request, String tenantName) {
		Tenant tenant = tenantService.getTenantByName(tenantName);
		String accessToken = tenant.getMlAccessToken();
		String url =  MELI_BASE_URL+"/items";
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "Bearer " + accessToken);
		HttpEntity<MlProductRequest> entity = new HttpEntity<>(request, headers);
		ResponseEntity<MeliItemDto> response = restTemplate.exchange(url, HttpMethod.POST, entity, MeliItemDto.class);

		return response.getBody();
	}

	public CategoryResponse[] getCategories(String siteId, String tenantName) {
		String url =  MELI_BASE_URL+"/sites/MLA/categories";
		
		Tenant tenant = tenantService.getTenantByName(tenantName);
		
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(tenant.getMlAccessToken());

		HttpEntity<Void> request = new HttpEntity<>(headers);

		ResponseEntity<CategoryResponse[]> response = restTemplate.exchange(url, HttpMethod.GET, request,
				CategoryResponse[].class);

		return response.getBody();
	}
	
	public AttributeResponse[] getCategoryAttributes(String tenantName, String categoryId) {
	    String url =  MELI_BASE_URL+"/categories/" + categoryId + "/attributes";

		Tenant tenant = tenantService.getTenantByName(tenantName);
		
	    HttpHeaders headers = new HttpHeaders();
	    
	    headers.setBearerAuth(tenant.getMlAccessToken());

	    HttpEntity<Void> request = new HttpEntity<>(headers);

	    ResponseEntity<AttributeResponse[]> response = restTemplate.exchange(
	        url,
	        HttpMethod.GET,
	        request,
	        AttributeResponse[].class
	    );

	    return response.getBody();
	}
	
	public Boolean stockUpdate(String itemId,String tenantName,MlUpdateProductRequest request){
		String url =  MELI_BASE_URL+"/items/"+itemId;
		
		Tenant tenant = tenantService.getTenantByName(tenantName);
		
		String accessToken = tenant.getMlAccessToken();
		
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "Bearer " + accessToken);
		
		HttpEntity<MlUpdateProductRequest> entity = new HttpEntity<>(request, headers);
		
		// SERIALIZACIÓN POR ITEM+TENANT CON COALESCING
		String lockKey = tenantName + ":" + itemId;
		Object lock = itemLocks.computeIfAbsent(lockKey, k -> new Object());
		synchronized (lock) {
			// Agregar a cola de coalescing
			ConcurrentLinkedQueue<PendingUpdate> queue = pendingUpdates.computeIfAbsent(lockKey, 
			k -> new ConcurrentLinkedQueue<>());
			queue.offer(new PendingUpdate(itemId, tenantName, request));
			
			System.out.println("📥 Agregado update a cola para " + lockKey + ", tamaño actual=" + queue.size());
			
			// Programar procesamiento después de un breve delay para permitir coalescing
			// Aumentamos el delay para permitir mejor agrupación de updates múltiples
			coalescingExecutor.schedule(() -> processCoalescedUpdate(lockKey), 500, TimeUnit.MILLISECONDS);
			
			return true; // Devolver true inmediatamente ya que el procesamiento es asíncrono
		}
	}

	private void processCoalescedUpdate(String lockKey) {
		Object lock = itemLocks.get(lockKey);
		if (lock == null) return;
		
		synchronized (lock) {
			ConcurrentLinkedQueue<PendingUpdate> queue = pendingUpdates.get(lockKey);
			if (queue == null || queue.isEmpty()) return;
			
			// Coalescer todos los updates pendientes en uno solo
			PendingUpdate firstUpdate = queue.poll();
			if (firstUpdate == null) return;
			
			String itemId = firstUpdate.getItemId();
			String tenantName = firstUpdate.getTenantName();
			
			// Obtener estado actual del item de Mercado Libre
			MeliItemDto currentItem = null;
			try {
				currentItem = getItemData(itemId, tenantName);
			} catch (Exception e) {
				System.err.println("Error obteniendo item actual para coalescing: " + e.getMessage());
			}
			
			// Construir el request coalesced combinando todas las actualizaciones
			MlUpdateProductRequest coalescedRequest = buildCoalescedRequest(queue, firstUpdate, currentItem);
			
			// Limpiar la cola
			queue.clear();
			
			// Ejecutar el update coalescido
			executeStockUpdate(itemId, tenantName, coalescedRequest);
		}
	}
	
	private MlUpdateProductRequest buildCoalescedRequest(ConcurrentLinkedQueue<PendingUpdate> queue, 
	                                                         PendingUpdate firstUpdate, 
	                                                         MeliItemDto currentItem) {
	    // Usar la primera request como base
	    MlUpdateProductRequest result = firstUpdate.getRequest();
	    
	    // Si hay más updates en la cola, aplicar las actualizaciones más recientes
	    while (!queue.isEmpty()) {
	        PendingUpdate nextUpdate = queue.poll();
	        if (nextUpdate != null && nextUpdate.getRequest() != null && nextUpdate.getRequest().getVariations() != null) {
	            // Actualizar las variaciones con los valores más recientes
	            for (VariationsDTO newVar : nextUpdate.getRequest().getVariations()) {
	                if (result.getVariations() != null) {
	                    // Buscar y actualizar la variación existente
	                    for (int i = 0; i < result.getVariations().size(); i++) {
	                        VariationsDTO existingVar = result.getVariations().get(i);
	                        if (existingVar.getId().equals(newVar.getId())) {
	                            result.getVariations().set(i, newVar); // Usar el valor más reciente
	                            break;
	                        }
	                    }
	                }
	            }
	        }
	    }
	    
	    return result;
	}
	
	private void executeStockUpdate(String itemId, String tenantName, MlUpdateProductRequest request) {
		String url = MELI_BASE_URL + "/items/" + itemId;
		
		Tenant tenant = tenantService.getTenantByName(tenantName);
		String accessToken = tenant.getMlAccessToken();
		
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "Bearer " + accessToken);
		HttpEntity<MlUpdateProductRequest> entity = new HttpEntity<>(request, headers);
		
		int maxAttempts = 5;
		long baseDelayMs = 300L;
		long maxDelayMs = 2000L;
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				ResponseEntity<MeliItemDto> resp = restTemplate
					.exchange(url, HttpMethod.PUT, entity, MeliItemDto.class);
				boolean ok = resp.getStatusCode().is2xxSuccessful();
				if (!ok) {
					System.out.println("MELI stockUpdate respondió con estado no exitoso: " + resp.getStatusCode());
				}
				return; // Éxito
			} catch (HttpClientErrorException e) {
				HttpStatusCode status = e.getStatusCode();
				String body = e.getResponseBodyAsString();
				System.out.println("MELI stockUpdate intento " + attempt + " falló con " + status + ": " + body);

				if (status.value() == 401) {
					try {
						System.out.println("Token ML posiblemente expirado. Intentando refrescar y reintentar...");
						MlTokenResponse refreshed = getAccessToken(tenantName);
						if (refreshed != null && refreshed.access_token != null && !refreshed.access_token.isEmpty()) {
							Tenant refreshedTenant = tenantService.getTenantByName(tenantName);
							headers.set("Authorization", "Bearer " + refreshedTenant.getMlAccessToken());
							entity = new HttpEntity<>(request, headers);
						}
					} catch (Exception ex) {
						System.out.println("Error al refrescar token ML: " + ex.getMessage());
					}
				}

				if (status.value() == 409 || status.value() == 429) {
					long delay = baseDelayMs;
					if (status.value() == 429 && e.getResponseHeaders() != null) {
						List<String> ra = e.getResponseHeaders().get("Retry-After");
						if (ra != null && !ra.isEmpty()) {
							try {
								delay = Math.max(delay, Long.parseLong(ra.get(0)) * 1000L);
							} catch (NumberFormatException ignore) {}
						}
					}
					if (attempt == maxAttempts) {
						System.out.println("Agotados los reintentos para estado " + status + ". Abortando actualización.");
						return;
					}
					try {
						long jitter = (long)(Math.random() * 150);
						long sleepMs = Math.min(delay, maxDelayMs) + jitter;
						System.out.println("Reintentando en " + sleepMs + " ms (estado=" + status + ")...");
						Thread.sleep(sleepMs);
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						return;
					}
					baseDelayMs = Math.min(baseDelayMs * 2, maxDelayMs);
					continue;
				}

				return;
			} catch (HttpServerErrorException e) {
				System.out.println("MELI stockUpdate intento " + attempt + " falló con 5xx: " + e.getStatusCode() + ", cuerpo=" + e.getResponseBodyAsString());
				if (attempt == maxAttempts) {
					return;
				}
				try {
					long jitter = (long)(Math.random() * 150);
					long sleepMs = Math.min(baseDelayMs, maxDelayMs) + jitter;
					System.out.println("Reintentando en " + sleepMs + " ms tras 5xx...");
					Thread.sleep(sleepMs);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					return;
				}
				baseDelayMs = Math.min(baseDelayMs * 2, maxDelayMs);
				continue;
			} catch (ResourceAccessException e) {
				System.out.println("MELI stockUpdate intento " + attempt + " error de red: " + e.getMessage());
				if (attempt == maxAttempts) {
					return;
				}
				try {
					long jitter = (long)(Math.random() * 150);
					long sleepMs = Math.min(baseDelayMs, maxDelayMs) + jitter;
					System.out.println("Reintentando en " + sleepMs + " ms tras error de red...");
					Thread.sleep(sleepMs);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					return;
				}
				baseDelayMs = Math.min(baseDelayMs * 2, maxDelayMs);
				continue;
			} catch (Exception e){
				System.out.println("Error inesperado en stockUpdate de MELI: " + e.getMessage());
				return;
			}
		}
	}

	public void notifyMercadoLibre(Product product) {
		MlUpdateProductRequest mlUpdateProductRequest = MlUpdateProductRequest.builder().variations(MlUtils.getVariations(product)).build();
		Boolean success = stockUpdate(product.getMercadoLibreId(),product.getTennantName(),mlUpdateProductRequest);
		if (!success) {
			throw new RuntimeException(
					"Failed to update stock on Mercado Libre for product: " + product.getMercadoLibreId());
		}
	}
	

	public void renewMlToken(Long tenantId) {
		
		try {
			Tenant tenant = tenantService.findById(tenantId);
			MlTokenResponse mlTokenResponse = getAccessToken(tenant.getName());
			if (mlTokenResponse == null || mlTokenResponse.access_token == null) {
				throw new RuntimeException("No se pudo obtener el token de acceso de Mercado Libre");
			}
			schedulerService.programarRenovacionTokenMl(tenant);
		} catch (Exception e) {
			System.out.println("Error al programar renovación de token ML: " + e.getMessage());
			System.out.println(e.getMessage());
		}
	}
}
