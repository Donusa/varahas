package varahas.main.services;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import varahas.main.dao.MlauDao;
import varahas.main.dto.StockUpdateDto;
import varahas.main.entities.Product;
import varahas.main.entities.Variations;
import varahas.main.enums.SourceChannel;
import varahas.main.output.MercadoLibreApiOutput;
import varahas.main.output.TiendaNubeApiOutput;
import varahas.main.repositories.ProductRepository;
import varahas.main.repositories.VariationRepository;

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
	            case MELI -> tiendaNubeApiOutput.notifyTiendaNube(product);
	            case TIENDA_NUBE -> mercadoLibreApiOutput.notifyMercadoLibre(product);
	        }
	    }
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
		return variationRepository.findByMlau(mlau);
	}


	public MlauDao findMlId(String str){
		
		if(str.contains("MLAU")){
			Pattern p = Pattern.compile("(ML[A-Z]*\\d+)(?=/|$)");
			Matcher matcher = p.matcher(str);
			if(matcher.find()){
				Variations variations = findByMlau(matcher.group(1));
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