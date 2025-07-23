package varahas.main.output;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.dao.IncorrectUpdateSemanticsDataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import varahas.main.dto.TnAuthDto;
import varahas.main.dto.TnStockUpdateDto;
import varahas.main.entities.Tenant;
import varahas.main.entities.TnProduct;

@Service
public class TiendaNubeApiOutput {
	
	@Value("${varahas.tn.api.agent:Varahas-testing 18768}")
	private String userAgent;
	
	@Value("${varahas.tn.api.id:6385727}")
    private String apiId;
	
	@Value("${varahas.tn.api.token:3754158629ec4945a94cbeca88789caf77a1dbb2}")
	private String apiToken;

	@Autowired
	private RestTemplate restTemplate;

	public String createProduct(TnProduct productData, Tenant tenant) {
		if (productData == null || tenant == null) {
			throw new IncorrectUpdateSemanticsDataAccessException("Datos del producto o tenant no pueden ser nulos");
		}
		
		String url = "https://api.nuvemshop.com.br/v1/" + apiId + "/products";
		
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("Authentication", "bearer " + tenant.getTiendaNubeAccessToken());
		headers.set("User-Agent", userAgent);
		
		HttpEntity<TnProduct> requestEntity = new HttpEntity<>(productData, headers);
		
		try {
			restTemplate.postForEntity(url, requestEntity, Object.class);
			return "Producto creado correctamente";
			
		} catch (Exception e) {
			throw new IncorrectUpdateSemanticsDataAccessException("Error al crear el producto: " + e.getMessage());
		}
		
	}

	public Object getAllProductsForUser(Tenant tenant) {
		if (tenant == null) {
			throw new IncorrectUpdateSemanticsDataAccessException("Tenant no puede ser nulo");
		}
		String url = "https://api.nuvemshop.com.br/v1/" + apiId + "/products";
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("Authentication", "bearer " + tenant.getTiendaNubeAccessToken());
		headers.set("User-Agent", userAgent);
		
		HttpEntity<String> requestEntity = new HttpEntity<>(headers);
		
		try {
			ResponseEntity<Object[]> response = restTemplate.exchange(
				    url,
				    HttpMethod.GET,
				    requestEntity,
				    Object[].class
				);

			if (response.getStatusCode().is2xxSuccessful()) {
				return response.getBody();
			} else {
				throw new IncorrectUpdateSemanticsDataAccessException(
						"Error al obtener los productos: " + response.getStatusCode());
			}
		} catch (Exception e) {
			throw new IncorrectUpdateSemanticsDataAccessException("Error al obtener los productos: " + e.getMessage());
		}
	}
	
	public Map<String,Object> getItemById(String itemId, Tenant tenant) {
		if (itemId == null || itemId.isEmpty() || tenant == null) {
			throw new IncorrectUpdateSemanticsDataAccessException("ID del producto o tenant no pueden ser nulos");
		}

		String url = "https://api.nuvemshop.com.br/v1/" + apiId + "/products/" + itemId;

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("Authentication", "bearer " + tenant.getTiendaNubeAccessToken());
		headers.set("User-Agent", userAgent);

		HttpEntity<String> requestEntity = new HttpEntity<>(headers);
		
		try {
			ResponseEntity<Object> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, Object.class);
			Object product = response.getBody();
		
			ObjectMapper mapper = new ObjectMapper();
			Map<String, Object> productMap = mapper.convertValue(
				product, new TypeReference<Map<String, Object>>() {}
			);
			/*
			String variantsUrl = "https://api.nuvemshop.com.br/v1/" + apiId + "/products/" + itemId + "/variants";
			try {
				ResponseEntity<List<Map<String,Object>>> variantsResponse = restTemplate.exchange(
					variantsUrl, HttpMethod.GET, requestEntity, new ParameterizedTypeReference<List<Map<String,Object>>>(){
					}
				);
				if (variantsResponse.getStatusCode().is2xxSuccessful() && variantsResponse.getBody() != null) {
					productMap.put("variants", variantsResponse.getBody());
				}
				System.out.println(variantsResponse.toString());
			} catch (Exception variantException) {
				System.out.println("No se pudieron obtener las variantes: " + variantException.getMessage());
			}*/
			return productMap;

		} catch (Exception e) {
			throw new IncorrectUpdateSemanticsDataAccessException("Error al obtener el producto: " + e.getMessage());
		}
	}


	public Object updateProduct(List<TnStockUpdateDto> productData, Tenant tenant, Long id) {
		String url = "https://api.nuvemshop.com.br/v1/" + apiId + "/products/" + id;

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("Authentication", "bearer " + tenant.getTiendaNubeAccessToken());
		headers.set("User-Agent", userAgent);

		HttpEntity<List<TnStockUpdateDto>> requestEntity = new HttpEntity<>(productData, headers);

		try {
			ResponseEntity<Object> response = restTemplate.exchange(url, HttpMethod.PUT, requestEntity, Object.class);
			System.out.println("Response status: " + response.getStatusCode());
			System.out.println("Response body: " + response.getBody());
			return response.getBody();

		} catch (Exception e) {
			throw new IncorrectUpdateSemanticsDataAccessException("Error al actualizar el producto: " + e.getMessage());
		}
	}


	public Object getCategories(Tenant tenant) {
		
		if (tenant == null) {
			throw new IncorrectUpdateSemanticsDataAccessException("Tenant no puede ser nulo");
		}

		String url = "https://api.nuvemshop.com.br/v1/" + apiId + "/categories";

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("Authentication", "bearer " + tenant.getTiendaNubeAccessToken());
		headers.set("User-Agent", userAgent);

		HttpEntity<String> requestEntity = new HttpEntity<>(headers);

		try {
			ResponseEntity<Object[]> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity,
					Object[].class);

			if (response.getStatusCode().is2xxSuccessful()) {
				return response.getBody();
			} else {
				throw new IncorrectUpdateSemanticsDataAccessException(
						"Error al obtener las categorías: " + response.getStatusCode());
			}
		} catch (Exception e) {
			throw new IncorrectUpdateSemanticsDataAccessException("Error al obtener las categorías: " + e.getMessage());
		}
		
	}
	
	public TnAuthDto tradeCodeForToken(String code) {
	    if (code == null || code.isEmpty()) {
	        throw new IncorrectUpdateSemanticsDataAccessException("Código no puede ser nulo o vacío");
	    }

	    String url = "https://www.tiendanube.com/apps/authorize/token";
	    String clientId = "18768";
	    String clientSecret = "07bdfd1f6929942799b4f5f4fed9d0b4c1bae811b6808eea";

	    HttpHeaders headers = new HttpHeaders();
	    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);


	    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
	    body.add("client_id", clientId);
	    body.add("client_secret", clientSecret);
	    body.add("grant_type", "authorization_code");
	    body.add("code", code);

	    HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);

	    try {
	        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
	        System.out.println(response.getBody());
	        ObjectMapper objectMapper = new ObjectMapper();
	        TnAuthDto dto = objectMapper.readValue(response.getBody(), TnAuthDto.class);
	        return dto;
	    } catch (Exception e) {
	        throw new IncorrectUpdateSemanticsDataAccessException("Error al intercambiar el código por el token: " + e.getMessage());
	    }
	
	}
	
	public List<Map<String,Object>> getVariants(String itemId,Tenant tenant){
		
		if (itemId == null || itemId.isEmpty() || tenant == null) {
			throw new IncorrectUpdateSemanticsDataAccessException("ID del producto o tenant no pueden ser nulos");
		}
		
		String variantsUrl = "https://api.nuvemshop.com.br/v1/" + apiId + "/products/" + itemId + "/variants";
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("Authentication", "bearer " + tenant.getTiendaNubeAccessToken());
		headers.set("User-Agent", userAgent);
		HttpEntity<String> requestEntity = new HttpEntity<>(headers);
		
		try {
			ResponseEntity<List<Map<String,Object>>> variantsResponse = restTemplate.exchange(
				variantsUrl, HttpMethod.GET, requestEntity, new ParameterizedTypeReference<List<Map<String,Object>>>(){
				}
			);
			System.out.println(variantsResponse.toString());
			
			return variantsResponse.getBody();
		} catch (Exception variantException) {
			System.out.println("No se pudieron obtener las variantes: " + variantException.getMessage());
			return null;
		}
	}
	
}
