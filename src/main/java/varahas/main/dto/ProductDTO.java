package varahas.main.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import varahas.main.entities.Product;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDTO {

	private Long id;
	private String name;
	private String description;
	private BigDecimal price;
	private Integer stock;
	private String mercadoLibreId;
	private byte isOnMercadoLibre;
	private byte isOnTiendaNube;
	
	public static List<ProductDTO> fromList(List<Product> prods) {
		return prods.stream().map(ProductDTO::from).toList();
    }
	
	public static ProductDTO from(Product prod) {
		return ProductDTO.builder()
                .id(prod.getId())
                .name(prod.getName())
                .description(prod.getDescription())
                .price(prod.getPrice())
                .stock(prod.getStock())
                .mercadoLibreId(prod.getMercadoLibreId())
                .isOnMercadoLibre(prod.getIsOnMercadoLibre())
                .isOnTiendaNube(prod.getIsOnTiendaNube())
                .build();
	}
}
