package varahas.main.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import varahas.main.entities.Product;
import varahas.main.entities.Variations;

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
	private String tiendaNubeId;
	private byte isOnMercadoLibre;
	private byte isOnTiendaNube;
	private List<Variations> variations;
	
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
                .variations(prod.getVariations())
                .build();
	}
}
