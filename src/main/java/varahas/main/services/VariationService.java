package varahas.main.services;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import varahas.main.dto.StockUpdate;
import varahas.main.entities.Product;
import varahas.main.entities.Variations;
import varahas.main.repositories.ProductRepository;
import varahas.main.repositories.VariationRepository;

@Service
public class VariationService {

    @Autowired
    private VariationRepository variationRepository;

    @Autowired
    private ProductRepository productRepository;

    @Transactional
    public void updateStockFromWebhook(Long variationId, int newRemoteStock) {
        Variations variation = variationRepository.findByIdForUpdate(variationId)
                .orElseThrow(() -> new IllegalArgumentException("Variation not found"));

        if (!Objects.equals(variation.getStock(), newRemoteStock)) {
            variation.setStock(newRemoteStock);
            variationRepository.save(variation);

            Product product = variation.getProduct();
            if (product != null) {
                int totalStock = product.getVariations().stream()
                        .mapToInt(Variations::getStock)
                        .sum();
                product.setStock(totalStock);
                productRepository.save(product);
            }
        }
    }

    public Optional<Variations> getByMeliId(String meliId) {
        return variationRepository.findByMeliId(meliId);
    }

    public Optional<Variations> getByTnId(String tnId) {
        return variationRepository.findByTnId(tnId);
    }
    
    public StockUpdate buildStockUpdate(Product product) {
        List<StockUpdate.VariationStockDto> variationDtos = product.getVariations().stream()
                .map(v -> StockUpdate.VariationStockDto.builder()
                        .variationId(v.getId())
                        .name(v.getName())
                        .stock(v.getStock())
                        .meliId(v.getMeliId())
                        .tnId(v.getTnId())
                        .build())
                .toList();

        int totalStock = variationDtos.stream()
                .mapToInt(StockUpdate.VariationStockDto::getStock)
                .sum();

        return StockUpdate.builder()
                .productId(product.getId())
                .totalStock(totalStock)
                .variations(variationDtos)
                .build();
    }
}