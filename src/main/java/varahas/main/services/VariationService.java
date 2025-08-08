package varahas.main.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import varahas.main.dao.MlauDao;
import varahas.main.dto.MeliItemDto;
import varahas.main.dto.MeliVariationDto;
import varahas.main.dto.StockUpdateDto;
import varahas.main.dto.VariationsDTO;
import varahas.main.entities.Product;
import varahas.main.entities.Tenant;
import varahas.main.entities.Variations;
import varahas.main.enums.SourceChannel;
import varahas.main.notifications.StockNotificationService;
import varahas.main.output.MercadoLibreApiOutput;
import varahas.main.output.TiendaNubeApiOutput;
import varahas.main.queue.StockUpdateQueueHandler;
import varahas.main.repositories.ProductRepository;
import varahas.main.repositories.VariationRepository;
import varahas.main.request.MlUpdateProductRequest;
import varahas.main.response.MlItemResponse;

@Service
public class VariationService {

	@Autowired
	private VariationRepository variationRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private TenantService tenantService;

	@Autowired
	private MercadoLibreApiOutput mercadoLibreApiOutput;

	@Autowired
	private TiendaNubeApiOutput tiendaNubeApiOutput;

	@Autowired
	private StockNotificationService stockNotificationService;

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

	public Optional<Variations> getByMeliId(Long meliId) {
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

	public MlauDao findMlId(String str) {

		if (str.contains("MLAU")) {
			Pattern p = Pattern.compile("(ML[A-Z]*\\d+)(?=/|$)");
			Matcher matcher = p.matcher(str);
			if (matcher.find()) {
				Variations variations = findByMlau(matcher.group(1));
				Product product = variations.getProduct();

				return MlauDao.builder().mla(product.getMercadoLibreId()).mlau(variations.getMlau()).build();
			}
		}
		throw new RuntimeException("No se encontro match");
	}

	@Transactional
	public void processStockDelta(StockUpdateQueueHandler.StockUpdateEvent event) {
		Long variationId = event.variationId();
		int newRemoteStock = event.stock();
		SourceChannel source = event.source();

		try {
			Variations variation = variationRepository.findById(variationId)
					.orElseThrow(() -> new RuntimeException("Variation no encontrada: " + variationId));

			Product product = variation.getProduct();
			Tenant tenant = tenantService.getTenantByName(product.getTennantName());

			System.out.println("🔄 Procesando delta de stock para variación: " + variationId + ", fuente: " + source);

			Integer meliCurrentStock = null;
			Integer tnCurrentStock = null;
			MeliItemDto meliItemDto = null;
			
			try {
				meliItemDto = mercadoLibreApiOutput.getItemData(product.getMercadoLibreId(),
						tenant.getName());
				if (variation.getMeliId() != null) {
					MlItemResponse mlItemResponse = mercadoLibreApiOutput
							.getCurrentMELIStock(product.getMercadoLibreId(), tenant.getName());
					if (mlItemResponse != null) {
						// meliCurrentStock = mlItemResponse.getAvailable_quantity();

						if (meliItemDto != null && meliItemDto.getVariations() != null) {
							for (MeliVariationDto mlVar : meliItemDto.getVariations()) {
								if (variation.getMeliId().equals(mlVar.getId())) {
									meliCurrentStock = mlVar.getAvailableQuantity();
									break;
								}
							}
						}
					}
				}
			} catch (Exception e) {
				System.err.println("Error obteniendo stock de MELI: " + e.getMessage());

			}

			if (variation.getTnId() != null && !variation.getTnId().isEmpty()) {
				try {
					var tnVariants = tiendaNubeApiOutput.getVariants(product.getTiendaNubeId(), tenant);
					if (tnVariants != null) {
						for (Map<String, Object> tnVar : tnVariants) {
							if (variation.getTnId().equals(String.valueOf(tnVar.get("id")))) {
								tnCurrentStock = (Integer) tnVar.get("stock");
								break;
							}
						}
					}
				} catch (Exception e) {
					System.err.println("Error obteniendo stock de TN: " + e.getMessage());
				}
			}

			int currentLocalStock = variation.getStock();
			int meliDelta = 0;
			int tnDelta = 0;

			if (meliCurrentStock != null) {
				meliDelta = currentLocalStock - meliCurrentStock;
			}
			if (tnCurrentStock != null) {
				tnDelta = currentLocalStock - tnCurrentStock;
			}

			int totalDelta = meliDelta + tnDelta;

			currentLocalStock -= totalDelta;

			System.out.println("📊 Stock actual local: " + variation.getStock() + ", MELI: " + meliCurrentStock
					+ ", TN: " + tnCurrentStock);
			System.out
					.println("📈 Delta MELI: " + meliDelta + ", Delta TN: " + tnDelta + ", Delta total: " + totalDelta);
			System.out.println("🎯 Nuevo stock local calculado: " + currentLocalStock);

			variation.setStock(currentLocalStock);
			variationRepository.save(variation);

			int totalProductStock = product.getVariations().stream().mapToInt(Variations::getStock).sum();
			product.setStock(totalProductStock);
			productRepository.save(product);

			if (variation.getMeliId() != null) {
				try {
					MlUpdateProductRequest mlRequest = new MlUpdateProductRequest();
					var variationList = new ArrayList<VariationsDTO>();
					if(meliItemDto != null){
						for(MeliVariationDto var :meliItemDto.getVariations()){
							variationList.add(VariationsDTO.builder()
									.available_quantity(
											variation.getId().equals(var.getId())?
											currentLocalStock:var.getAvailableQuantity()
											)
									.id(var.getId()).build()
									);
							}
						
					}
					
					mlRequest.setVariations(variationList);

					boolean mlSuccess = mercadoLibreApiOutput.stockUpdate(product.getMercadoLibreId(), tenant.getName(),
							mlRequest);
					if (mlSuccess) {
						System.out.println("✅ Stock actualizado en MELI: " + currentLocalStock);
					} else {
						System.err.println("❌ Error actualizando stock en MELI");
					}
				} catch (Exception e) {
					System.err.println("❌ Error actualizando MELI: " + e.getMessage());
				}
			}

			// Actualizar Tienda Nube
			if (variation.getTnId() != null && !variation.getTnId().isEmpty()) {
				try {
					tiendaNubeApiOutput.updateVariant(Long.valueOf(product.getTiendaNubeId()),
							Long.valueOf(variation.getTnId()), currentLocalStock, tenant);
					System.out.println("✅ Stock actualizado en TN: " + currentLocalStock);
				} catch (Exception e) {
					System.err.println("❌ Error actualizando TN: " + e.getMessage());
				}
			}

			try {
				StockUpdateDto stockUpdate = buildStockUpdate(product);
				stockNotificationService.sendUpdate(stockUpdate);
				System.out.println("📢 Notificación de stock enviada");
			} catch (Exception e) {
				System.err.println("❌ Error enviando notificación: " + e.getMessage());
			}

		} catch (Exception e) {
			System.err.println("❌ Error procesando delta de stock: " + e.getMessage());
			throw new RuntimeException("Error procesando delta de stock", e);
		}
	}
}