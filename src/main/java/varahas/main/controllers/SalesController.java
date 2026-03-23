package varahas.main.controllers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import varahas.main.entities.Product;
import varahas.main.entities.SalesHistory;
import varahas.main.entities.Tenant;
import varahas.main.entities.Variations;
import varahas.main.enums.SourceChannel;
import varahas.main.queue.StockUpdateQueueHandler;
import varahas.main.repositories.SalesHistoryRepository;
import varahas.main.services.ProductService;
import varahas.main.services.TenantService;
import varahas.main.services.VariationService;

@RestController
@RequestMapping("/api/sales")
public class SalesController {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private ProductService productService;
    
    @Autowired
    private VariationService variationService;
    
    @Autowired
    private StockUpdateQueueHandler stockUpdateQueueHandler;
    
    @Autowired
    private SalesHistoryRepository salesHistoryRepository;

    @PostMapping("/create")
    public ResponseEntity<?> createSale(@RequestParam String tenantName, @RequestBody List<SaleItemDto> items) {
        try {
            Tenant tenant = tenantService.getTenantByName(tenantName);
            if (tenant == null) {
                return ResponseEntity.badRequest().body("Tenant no encontrado: " + tenantName);
            }

            if (items == null || items.isEmpty()) {
                 return ResponseEntity.badRequest().body("No se proporcionaron items para la venta.");
            }
            
            List<Product> soldProducts = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;
            
            for (SaleItemDto item : items) {
                Long variationId = item.getVariationId();
                Integer quantity = item.getQuantity();
                
                if (quantity == null || quantity <= 0) {
                     return ResponseEntity.badRequest().body("Cantidad inválida para la variación: " + variationId);
                }
                
                Variations variation = variationService.getById(variationId);
                if (variation == null) {
                    return ResponseEntity.badRequest().body("Variación no encontrada: " + variationId);
                }
                
                Product product = variation.getProduct();
                if (!product.getTennantName().equals(tenantName)) {
                     return ResponseEntity.badRequest().body("La variación " + variationId + " no pertenece al tenant " + tenantName);
                }
                
                // Actualizar stock local y encolar evento
                Integer currentStock = variation.getStock();
                if (currentStock < quantity) {
                     return ResponseEntity.badRequest().body("Stock insuficiente para la variación: " + variationId + ". Solicitado: " + quantity + ", Disponible: " + currentStock);
                }
                
                Integer newStock = currentStock - quantity;
                // Enqueue event to update ML and TN
                stockUpdateQueueHandler.enqueueEvent(variationId, newStock, SourceChannel.LOCAL);
                
                // Add product reference 'quantity' times to maintain history count in simple list
                for (int i = 0; i < quantity; i++) {
                    soldProducts.add(product);
                }
                
                if (product.getPrice() != null) {
                    total = total.add(product.getPrice().multiply(new BigDecimal(quantity)));
                }
            }
            
            // Si todo salió bien (no hubo excepciones en el loop), guardar historial
            SalesHistory history = SalesHistory.builder()
                    .tenant(tenant)
                    .products(soldProducts)
                    .total(total)
                    .facturado(false) // Por defecto
                    .build();
            
            salesHistoryRepository.save(history);

            return ResponseEntity.ok("Venta procesada y guardada en historial. ID: " + history.getId());

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error al procesar la venta: " + e.getMessage());
        }
    }
}
