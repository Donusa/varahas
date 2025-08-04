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
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import varahas.main.dto.AttributeResponse;
import varahas.main.dto.CategoryResponse;
import varahas.main.dto.MeliItemDto;
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

@Service
public class MercadoLibreApiOutput {

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
		
		try {
		return "200 OK".equals(
				restTemplate
					.exchange(url, HttpMethod.PUT, entity, MeliItemDto.class)
					.getStatusCode()
					.toString()
				);
		
		}catch (Exception e){
			System.out.println(e.getMessage());
			return false;
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
