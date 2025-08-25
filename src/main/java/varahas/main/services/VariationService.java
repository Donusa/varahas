package varahas.main.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import varahas.main.configuration.ApplicationConfig;
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

	private final ApplicationConfig applicationConfig;

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

	VariationService(ApplicationConfig applicationConfig) {
		this.applicationConfig = applicationConfig;
	}

	public Optional<Variations> getByMeliId(Long meliId) {
		return variationRepository.findByMeliId(meliId);
	}

	public Optional<Variations> getByTnId(String tnId) {
		return variationRepository.findByTnId(tnId);
	}
	
	public Variations getById(Long id){
		return variationRepository.findById(id).orElse(null);
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
	public void processStockDelta(StockUpdateQueueHandler.StockEvent event) {
		Long variationId = event.variationId();
		SourceChannel source = event.source();

		try {
			System.out.println("🟦 Iniciando processStockDelta: variationId=" + variationId + ", source=" + source + ", tipo=" + event.getClass().getSimpleName());
			Variations variation = variationRepository.findById(variationId)
					.orElseThrow(() -> new RuntimeException("Variation no encontrada: " + variationId));

			Product product = variation.getProduct();
			Tenant tenant = tenantService.getTenantByName(product.getTennantName());
			System.out.println("🟪 Producto=" + product.getId() + ", tenant=" + (tenant != null ? tenant.getName() : null));

			Integer meliCurrentStock = null;
			Integer tnCurrentStock = null;
			MeliItemDto meliItemDto = null;

			if (event instanceof StockUpdateQueueHandler.LocalStockUpdateEvent localEvent) {
				int newLocalStock = localEvent.stock();
				System.out.println("📱 Evento LOCAL: variación=" + variationId + ", nuevo stock=" + newLocalStock);
				variation.setStock(newLocalStock);
				variationRepository.save(variation);
				int totalProductStock = product.getVariations().stream().mapToInt(Variations::getStock).sum();
				product.setStock(totalProductStock);
				productRepository.save(product);
				System.out.println("📤 Sincronizando canales externos con stock=" + newLocalStock);
				notifyExternalChannels(product, variation, tenant, newLocalStock);
				try {
					StockUpdateDto stockUpdate = buildStockUpdate(product);
					stockNotificationService.sendUpdate(stockUpdate);
					System.out.println("📣 Notificación enviada");
				} catch (Exception e) {
					System.err.println("Error enviando notificación: " + e.getMessage());
				}
				System.out.println("🟩 Finalizado processStockDelta LOCAL para variación=" + variationId);
				return;
			}

			try {
				if (product.getMercadoLibreId() != null) {
					meliItemDto = mercadoLibreApiOutput.getItemData(product.getMercadoLibreId(), tenant.getName());
				}
				if (variation.getMeliId() != null && product.getMercadoLibreId() != null) {
					MlItemResponse mlItemResponse = mercadoLibreApiOutput
							.getCurrentMELIStock(product.getMercadoLibreId(), tenant.getName());
					if (mlItemResponse != null) {
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
								Object stockObj = tnVar.get("stock");
								if (stockObj instanceof Number) {
									tnCurrentStock = ((Number) stockObj).intValue();
								}
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

			System.out.println("📊 Local=" + variation.getStock() + ", MELI=" + meliCurrentStock + ", TN=" + tnCurrentStock);
			System.out.println("📈 Delta MELI=" + meliDelta + ", Delta TN=" + tnDelta + ", Total=" + totalDelta);
			System.out.println("🎯 Nuevo local calculado=" + currentLocalStock);

			variation.setStock(currentLocalStock);
			variationRepository.save(variation);

			int totalProductStock = product.getVariations().stream().mapToInt(Variations::getStock).sum();
			product.setStock(totalProductStock);
			productRepository.save(product);
			System.out.println("💾 Guardado producto y variación con nuevo stock");

			if (variation.getMeliId() != null) {
				try {
					MlUpdateProductRequest mlRequest = new MlUpdateProductRequest();
					var variationList = new ArrayList<VariationsDTO>();
					if (meliItemDto != null && meliItemDto.getVariations() != null) {
						for (MeliVariationDto var : meliItemDto.getVariations()) {
							Integer stockToUse;
							if (variation.getMeliId().equals(var.getId())) {
								stockToUse = currentLocalStock;
								System.out.println("📝 Actualizando variación MELI " + var.getId() + " con stock calculado=" + currentLocalStock);
							} else {
								Integer localStock = null;
								for (Variations localVar : product.getVariations()) {
									if (localVar.getMeliId() != null && localVar.getMeliId().equals(var.getId())) {
										localStock = localVar.getStock();
										break;
									}
								}
								stockToUse = localStock != null ? localStock : var.getAvailableQuantity();
								System.out.println("📝 Manteniendo variación MELI " + var.getId() + " con stock=" + stockToUse + " (local=" + localStock + ", meli=" + var.getAvailableQuantity() + ")");
							}
							
							variationList.add(VariationsDTO.builder()
									.available_quantity(stockToUse)
									.id(var.getId()).build());
						}
					}
					mlRequest.setVariations(variationList);
					System.out.println("📤 Enviando actualización a MELI para item=" + product.getMercadoLibreId() + " con " + variationList.size() + " variaciones");
					mercadoLibreApiOutput.stockUpdate(product.getMercadoLibreId(), tenant.getName(), mlRequest);
				} catch (Exception e) {
					System.err.println("Error actualizando MELI: " + e.getMessage());
				}
			}

			if (variation.getTnId() != null && !variation.getTnId().isEmpty()) {
				try {
					System.out.println("📤 Enviando actualización a TN para producto=" + product.getTiendaNubeId() + ", variante=" + variation.getTnId() + ", stock=" + currentLocalStock);
					tiendaNubeApiOutput.updateVariant(Long.valueOf(product.getTiendaNubeId()),
							Long.valueOf(variation.getTnId()), currentLocalStock, tenant);
				} catch (Exception e) {
					System.err.println("Error actualizando TN: " + e.getMessage());
				}
			}

			try {
				StockUpdateDto stockUpdate = buildStockUpdate(product);
				stockNotificationService.sendUpdate(stockUpdate);
				System.out.println("📣 Notificación enviada");
			} catch (Exception e) {
				System.err.println("Error enviando notificación: " + e.getMessage());
			}

			System.out.println("🟩 Finalizado processStockDelta REMOTO para variación=" + variationId);
		} catch (Exception e) {
			System.err.println("Error procesando delta de stock: " + e.getMessage());
			throw new RuntimeException("Error procesando delta de stock", e);
		}
	}

	private void notifyExternalChannels(Product product, Variations variation, Tenant tenant, int stock) {
		System.out.println("🔔 notifyExternalChannels: product=" + product.getId() + ", variation=" + variation.getId() + ", stock=" + stock);
		try {
			MeliItemDto meli = null;
			try {
				if (product.getMercadoLibreId() != null) {
					meli = mercadoLibreApiOutput.getItemData(product.getMercadoLibreId(), tenant.getName());
				}
			} catch (Exception ex) {
				System.err.println("Error obteniendo datos de MELI: " + ex.getMessage());
			}

			if (variation.getMeliId() != null && meli != null) {
				try {
					MlUpdateProductRequest mlRequest = new MlUpdateProductRequest();
					var variationList = new ArrayList<VariationsDTO>();
					
					for (MeliVariationDto var : meli.getVariations()) {
						Integer stockToUse;
						if (variation.getMeliId().equals(var.getId())) {
							stockToUse = stock;
							System.out.println("📝 Actualizando variación MELI " + var.getId() + " con stock=" + stock);
						} else {
							Integer localStock = null;
							for (Variations localVar : product.getVariations()) {
								if (localVar.getMeliId() != null && localVar.getMeliId().equals(var.getId())) {
									localStock = localVar.getStock();
									break;
								}
							}
							stockToUse = localStock != null ? localStock : var.getAvailableQuantity();
							System.out.println("📝 Manteniendo variación MELI " + var.getId() + " con stock=" + stockToUse + " (local=" + localStock + ", meli=" + var.getAvailableQuantity() + ")");
						}
						
						variationList.add(VariationsDTO.builder()
								.available_quantity(stockToUse)
								.id(var.getId())
								.build());
					}
					
					mlRequest.setVariations(variationList);
					System.out.println("📤 Enviando actualización a MELI desde notifyExternalChannels para item=" + product.getMercadoLibreId() + " con " + variationList.size() + " variaciones");
					mercadoLibreApiOutput.stockUpdate(product.getMercadoLibreId(), tenant.getName(), mlRequest);
				} catch (Exception e) {
					System.err.println("Error actualizando MELI: " + e.getMessage());
				}
			}

			if (variation.getTnId() != null && !variation.getTnId().isEmpty()) {
				try {
					System.out.println("📤 Enviando actualización a TN desde notifyExternalChannels para producto=" + product.getTiendaNubeId() + ", variante=" + variation.getTnId() + ", stock=" + stock);
					tiendaNubeApiOutput.updateVariant(Long.valueOf(product.getTiendaNubeId()),
							Long.valueOf(variation.getTnId()), stock, tenant);
				} catch (Exception e) {
					System.err.println("Error actualizando TN: " + e.getMessage());
				}
			}

		} catch (Exception e) {
			System.err.println("Error al actualizar: "+ e.getMessage());
		}
	}

	@Transactional
	public Product removeVariation(Long variationId, String scope) {
		if (variationId == null || scope == null) {
			throw new IllegalArgumentException("variationId and scope are required");
		}
		String normalized = scope.trim().toUpperCase();
		Variations variation = variationRepository.findById(variationId)
				.orElseThrow(() -> new RuntimeException("Variation not found: " + variationId));
		Product product = variation.getProduct();

		switch (normalized) {
			case "FULL": {
				if (product != null && product.getVariations() != null) {
					product.getVariations().removeIf(v -> v.getId().equals(variationId));
					
					int totalProductStock = product.getVariations().stream().mapToInt(Variations::getStock).sum();
					product.setStock(totalProductStock);
					product.setIsOnMercadoLibre((byte) 0);
					product.setIsOnTiendaNube((byte)0);
					product.setMercadoLibreId(null);
					product.setTiendaNubeId(null);
					productRepository.save(product);
				} else {
					variationRepository.deleteById(variationId);
				}
				System.out.println("🗑️ FULL removal of variation=" + variationId + " for product=" + (product != null ? product.getId() : null));
				return product;
			}
			case "ML": {
				variation.setMeliId(null);
				variation.setMlau(null);
				variationRepository.save(variation);
				product.setIsOnMercadoLibre((byte) 0);
				product.setMercadoLibreId(null);
				System.out.println("🔗 Unlinked ML for variation=" + variationId);
				return product;
			}
			case "TN": {
				variation.setTnId(null);
				variationRepository.save(variation);
				product.setIsOnTiendaNube((byte)0);
				product.setTiendaNubeId(null);
				System.out.println("🔗 Unlinked TN for variation=" + variationId);
				return product;
			}
			default:
				throw new IllegalArgumentException("Invalid scope. Use FULL, ML, or TN");
		}
	}
}