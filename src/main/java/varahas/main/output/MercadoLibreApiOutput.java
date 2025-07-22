package varahas.main.output;

import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import varahas.main.dao.MlauDao;
import varahas.main.dto.AttributeResponse;
import varahas.main.dto.CategoryResponse;
import varahas.main.dto.MeliItemDto;
import varahas.main.dto.MlItemResponse;
import varahas.main.dto.MlProductRequest;
import varahas.main.dto.MlTokenResponse;
import varahas.main.dto.MlUpdateProductRequest;
import varahas.main.dto.MlUserItemsResponse;
import varahas.main.entities.Product;
import varahas.main.entities.Tenant;
import varahas.main.entities.Variations;
import varahas.main.services.TenantService;
import varahas.main.services.VariationService;

@Service
public class MercadoLibreApiOutput {

	@Value("${mercadolibre.client.id:7214647968735209}")
	private String clientId;

	@Value("${mercadolibre.client.secret:yivdPpntv5NH523KUhpyKqUltP6lWrAq}")
	private String clientSecret;

	@Autowired
	private RestTemplate restTemplate;

	@Autowired
	private TenantService tenantService;
	
	@Autowired
	private VariationService variationService;


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
		String url = "https://api.mercadolibre.com/oauth/token";

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
		
		System.out.println("id del cliente: " + clientId);

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
		String url = "https://api.mercadolibre.com/users/" + tenant.getMlUserId() + "/items/search";

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
		System.out.println("getItemData 177");
		Tenant tenant = tenantService.getTenantByName(tenantName);
		String url = "https://api.mercadolibre.com/items/" + itemId;
		System.out.println(itemId + "--"+ tenant.getName());
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
		String url = "https://api.mercadolibre.com/items/" + meliId;

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(tenant.getMlAccessToken());

		HttpEntity<String> entity = new HttpEntity<>(headers);

		ResponseEntity<MlItemResponse> response = restTemplate.exchange(url, HttpMethod.GET, entity,
				MlItemResponse.class);

		return response.getBody();
	}

	public MeliItemDto postProduct(MlProductRequest request, String tenantName) {
		System.out.println("MercadoLibreApiOutput.postProduct 225");
		Tenant tenant = tenantService.getTenantByName(tenantName);
		String accessToken = tenant.getMlAccessToken();
		String url = "https://api.mercadolibre.com/items";
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "Bearer " + accessToken);
		HttpEntity<MlProductRequest> entity = new HttpEntity<>(request, headers);
		System.out.println("Request:" + request);
		ResponseEntity<MeliItemDto> response = restTemplate.exchange(url, HttpMethod.POST, entity, MeliItemDto.class);

		return response.getBody();
	}

	public CategoryResponse[] getCategories(String siteId, String tenantName) {
		String url = "https://api.mercadolibre.com/sites/MLA/categories";
		
		Tenant tenant = tenantService.getTenantByName(tenantName);
		
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(tenant.getMlAccessToken());

		HttpEntity<Void> request = new HttpEntity<>(headers);

		ResponseEntity<CategoryResponse[]> response = restTemplate.exchange(url, HttpMethod.GET, request,
				CategoryResponse[].class);

		return response.getBody();
	}
	
	public AttributeResponse[] getCategoryAttributes(String tenantName, String categoryId) {
	    String url = "https://api.mercadolibre.com/categories/" + categoryId + "/attributes";

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
		String url = "https://api.mercadolibre.com/items/"+itemId;
		
		Tenant tenant = tenantService.getTenantByName(tenantName);
		
		String accessToken = tenant.getMlAccessToken();
		
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "Bearer " + accessToken);
		
		HttpEntity<MlUpdateProductRequest> entity = new HttpEntity<>(request, headers);
		
		System.out.println("Request:" + request);
		try {
		ResponseEntity<MeliItemDto> response = restTemplate.exchange(url, HttpMethod.PUT, entity, MeliItemDto.class);
		if("200 OK".equals(response.getStatusCode().toString())){
			return true;
		}
		
		return false;
		
		
		}catch (Exception e){
			System.out.println(e.getMessage());
			return false;
		}
		
	}
	
	
	public MlauDao findMlId(String str){
		
		if(str.contains("MLAU")){
			Pattern p = Pattern.compile("(ML[A-Z]*\\d+)(?=/|$)");
			Matcher matcher = p.matcher(str);
			if(matcher.find()){
				Variations variations = variationService.findByMlau(matcher.group(1));
				Product product = variations.getProduct();
				
				
				return MlauDao.builder()
						.mla(product.getMercadoLibreId())
						.mlau(variations.getMlau())
						.build();
			}
		}
		throw new RuntimeException("No se encontro match");
	}

}
