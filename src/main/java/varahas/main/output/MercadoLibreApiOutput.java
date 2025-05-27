package varahas.main.output;

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

import varahas.main.dto.MeliItemDto;
import varahas.main.dto.MlItemResponse;
import varahas.main.dto.MlProductRequest;
import varahas.main.dto.MlTokenResponse;
import varahas.main.dto.MlUserItemsResponse;
import varahas.main.entities.Product;
import varahas.main.entities.TenantAccessToken;
import varahas.main.services.ProductService;
import varahas.main.services.TenantAccessTokenService;

@Service
public class MercadoLibreApiOutput {

	@Value("${mercadolibre.client.id:3670283929615433}")
	private String clientId;

	@Value("${mercadolibre.client.secret:gEVsQhSNYzDRXLpLASEK8Iwy56LM8hvo}")
	private String clientSecret;

	private final RestTemplate restTemplate;

	@Autowired
	private ProductService productService;

	@Autowired
	private TenantAccessTokenService tenantAccessService;

	public MercadoLibreApiOutput() {
		this.restTemplate = new RestTemplate();
	}

	public MlTokenResponse getAccessToken(Long tenantId) {

		TenantAccessToken accessToken;
		
		try {
			accessToken = tenantAccessService.getAccessTokenByTenantId(tenantId);
			}catch(Exception e){
				accessToken = tenantAccessService.saveNew(tenantId);
			}

		String url = "https://api.mercadolibre.com/oauth/token";

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

		MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
		map.add("grant_type", "refresh_token");
		map.add("client_id", clientId);
		map.add("client_secret", clientSecret);
		map.add("refresh_token", accessToken.getRefreshToken());

		HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

		try {
			ResponseEntity<MlTokenResponse> response = restTemplate.postForEntity(url, request, MlTokenResponse.class);

			MlTokenResponse tokenResponse = response.getBody();
			if (tokenResponse != null) {
				accessToken.setAccessToken(tokenResponse.access_token);
				accessToken.setRefreshToken(tokenResponse.refresh_token);
				tenantAccessService.save(accessToken);
				return tokenResponse;
			} else {
				System.out.println("Error: Token response is null");
				return new MlTokenResponse();
			}
		} catch (Exception e) {
			System.out.println("Error al obtener refresh token de Mercado Libre: " + e.getMessage());
			MlTokenResponse errorResponse = new MlTokenResponse();
			errorResponse.access_token = "";
			errorResponse.refresh_token = "";
			return errorResponse;
		}
	}

	public Object tradeAccessToken(String code, Long tenantId) {
		TenantAccessToken accessToken;
		try {
		accessToken = tenantAccessService.getAccessTokenByTenantId(tenantId);
		}catch(Exception e){
			System.out.println("MercadoLibreOutput.tradeAcessToken 88");
			accessToken = tenantAccessService.saveNew(tenantId);
		}
		String url = "https://api.mercadolibre.com/oauth/token";

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

		MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
		map.add("grant_type", "authorization_code");
		map.add("client_id", clientId);
		map.add("client_secret", clientSecret);
		map.add("code", code);
		map.add("redirect_uri", "https://benedicto17.com.ar/");
		HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

		try {
			ResponseEntity<MlTokenResponse> response = restTemplate.postForEntity(url, request, MlTokenResponse.class);

			MlTokenResponse tokenResponse = response.getBody();
			if (tokenResponse != null) {
				accessToken.setAccessToken(tokenResponse.access_token);
				accessToken.setRefreshToken(tokenResponse.refresh_token);
				tenantAccessService.save(accessToken);
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

	public List<String> getAllItemsForUser(String userId, Long tenantId) {
		
		TenantAccessToken accessToken = tenantAccessService.getAccessTokenByTenantId(tenantId);
		String url = "https://api.mercadolibre.com/users/" + userId + "/items/search";

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(accessToken.getAccessToken());

		HttpEntity<String> entity = new HttpEntity<>(headers);

		ResponseEntity<MlUserItemsResponse> response = restTemplate.exchange(url, HttpMethod.GET, entity,
				MlUserItemsResponse.class);

		MlUserItemsResponse itemsResponse = response.getBody();
		return itemsResponse != null ? itemsResponse.getResults() : null;
	}

	public MeliItemDto getItemData(String itemId,Long tenantId) {
		TenantAccessToken accessToken = tenantAccessService.getAccessTokenByTenantId(tenantId);
		String url = "https://api.mercadolibre.com/items/" + itemId;

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(accessToken.getAccessToken());

		HttpEntity<String> entity = new HttpEntity<>(headers);

		ResponseEntity<MeliItemDto> response = restTemplate.exchange(url, HttpMethod.GET, entity, MeliItemDto.class);

		return response.getBody();
	}

	public Integer syncStockWithMercadoLibre(Long productId, Long stockId,Long tenantId) {
		Product product = productService.getProduct(productId);

		if (product.getIsOnMercadoLibre() == 0 || product.getMercadoLibreId() == null
				|| product.getMercadoLibreId().isEmpty()) {
			throw new RuntimeException("Product does not have a Mercado Libre ID");
		}

		Integer localMlStock = product.getMeliItem().getAvailableQuantity();
		Integer currentMlStock = getAvailableQuantity(product.getMercadoLibreId(), tenantId);

		if (currentMlStock == null) {
			throw new RuntimeException("Failed to retrieve stock from Mercado Libre");
		}

		Integer salesOnMl = localMlStock - currentMlStock;

		if (salesOnMl > 0) {
			Integer updatedStock = product.getStock() - salesOnMl;

			if (updatedStock < 0) {
				updatedStock = 0;
			}

			product.setStock(updatedStock);

			product.getMeliItem().setAvailableQuantity(currentMlStock);

			productService.saveProduct(product);
		}

		return salesOnMl > 0 ? salesOnMl : 0;
	}

	public Integer getAvailableQuantity(String meliId,Long tenantId) {
		MlItemResponse item = getCurrentMELIStock(meliId,tenantId);
		return item != null ? item.getAvailable_quantity() : null;
	}

	public MlItemResponse getCurrentMELIStock(String meliId,Long tenantId) {
		TenantAccessToken accessToken = tenantAccessService.getAccessTokenByTenantId(tenantId);
		String url = "https://api.mercadolibre.com/items/" + meliId;

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(accessToken.getAccessToken());

		HttpEntity<String> entity = new HttpEntity<>(headers);

		ResponseEntity<MlItemResponse> response = restTemplate.exchange(url, HttpMethod.GET, entity,
				MlItemResponse.class);

		return response.getBody();
	}
	
	public MeliItemDto postProduct(MlProductRequest request,Long tenantId) {
		System.out.println("MercadoLibreApiOutput.postProduct 225");
		TenantAccessToken accessToken = tenantAccessService.getAccessTokenByTenantId(tenantId);
		System.out.println("Aca el tenant:"+ accessToken);
		String url = "https://api.mercadolibre.com/items";
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "Bearer "+accessToken.getAccessToken());
		HttpEntity<MlProductRequest> entity = new HttpEntity<>(request,headers);
		System.out.println("Request:"+request);
		ResponseEntity<MeliItemDto> response = restTemplate.exchange(url, HttpMethod.POST,entity,MeliItemDto.class);
		
		return response.getBody();
		
	}
}
