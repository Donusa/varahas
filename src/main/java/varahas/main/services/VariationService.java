package varahas.main.services;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import varahas.main.dto.StockUpdateDto;
import varahas.main.dto.TnInventoryLevelsDto;
import varahas.main.dto.TnStockUpdateDto;
import varahas.main.dto.TnVariationUpdateDto;
import varahas.main.entities.Product;
import varahas.main.entities.Variations;
import varahas.main.enums.SourceChannel;
import varahas.main.output.MercadoLibreApiOutput;
import varahas.main.output.TiendaNubeApiOutput;
import varahas.main.repositories.ProductRepository;
import varahas.main.repositories.VariationRepository;
import varahas.main.request.MlUpdateProductRequest;
import varahas.main.utils.MlUtils;

@Service
public class VariationService {

	@Autowired
	private VariationRepository variationRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private MercadoLibreApiOutput mercadoLibreApiOutput;

	@Autowired
	private TiendaNubeApiOutput tiendaNubeApiOutput;

	@Autowired
	private TenantService tenantService;

	
	
	@Transactional
	public void updateStockFromWebhook(Long variationId, int newRemoteStock, SourceChannel sourceChannel) {
		Variations variation = variationRepository.findByIdForUpdate(variationId)
	            .orElseThrow(() -> new IllegalArgumentException("Variation not found"));

	    Product product = variation.getProduct();
	    if (product == null) {
	        throw new IllegalArgumentException("Product not found for variation");
	    }

	    if (!Objects.equals(variation.getStock(), newRemoteStock)) {
	        variation.setStock(newRemoteStock);
	        variationRepository.save(variation);

	        int totalStock = product.getVariations().stream().mapToInt(Variations::getStock).sum();
	        product.setStock(totalStock);
	        productRepository.save(product);

	        switch (sourceChannel) {
	            case MELI -> notifyTiendaNube(product);
	            case TIENDA_NUBE -> notifyMercadoLibre(product);
	        }
	    }
	}

	private void notifyMercadoLibre(Product product) {
		MlUpdateProductRequest mlUpdateProductRequest = MlUpdateProductRequest.builder().variations(MlUtils.getVariations(product)).build();
		Boolean success = mercadoLibreApiOutput.stockUpdate(product.getMercadoLibreId(),product.getTennantName(),mlUpdateProductRequest);
		if (!success) {
			throw new RuntimeException(
					"Failed to update stock on Mercado Libre for product: " + product.getMercadoLibreId());
		}
	}

	private void notifyTiendaNube(Product product) {
	    List<TnStockUpdateDto> tnUpdate = product.getVariations().stream().map(v ->
	        TnStockUpdateDto.builder()
	            .tnId(v.getTnId())
	            .variants(List.of(TnVariationUpdateDto.builder()
	                .id(v.getId())
	                .inventoryLevels(List.of(TnInventoryLevelsDto.builder()
	                    .stock(v.getStock()).build()))
	                .build()))
	            .build()
	    ).toList();

	    tiendaNubeApiOutput.updateProduct(
	        tnUpdate,
	        tenantService.getTenantByName(product.getTennantName()),
	        Long.valueOf(product.getVariations().get(0).getTnId()) 
	    );
	}
	
	
	
	
	public Optional<Variations> getByMeliId(String meliId) {
		return variationRepository.findByMeliId(meliId);
	}

	public Optional<Variations> getByTnId(String tnId) {
		return variationRepository.findByTnId(tnId);
	}

	public StockUpdateDto buildStockUpdate(Product product) {
		List<StockUpdateDto.VariationStockDto> variationDtos = product
				.getVariations().stream().map(v -> StockUpdateDto.VariationStockDto.builder().variationId(v.getId())
						.name(v.getName()).stock(v.getStock()).meliId(v.getMeliId()).tnId(v.getTnId()).build())
				.toList();

		int totalStock = variationDtos.stream().mapToInt(StockUpdateDto.VariationStockDto::getStock).sum();

		return StockUpdateDto.builder().productId(product.getId()).totalStock(totalStock).variations(variationDtos)
				.build();
	}

	public Variations findByMlau(String mlau) {
		return variationRepository.findByMlau(mlau).orElseThrow(() -> new RuntimeException("Mlau no encontrado"));
	}

	public Optional<Variations> findByMlau(String mlau, Boolean dato) {
		System.out.println("findByMlau 81");
		System.out.println("Mlau:" + mlau);
		return variationRepository.findByMlau(mlau);
	}

}