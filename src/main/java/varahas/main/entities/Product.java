package varahas.main.entities;

import java.math.BigDecimal;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	private String name;
	private String description;
	private BigDecimal price;
	private Integer stock;
	private String mercadoLibreId;
	private byte isOnMercadoLibre;
	private byte isOnTiendaNube;
	private String tennantName;
	@OneToOne(cascade = CascadeType.ALL)
	@JoinColumn(name = "meli_item_id")
	private MeliItem meliItem;
	@OneToOne(cascade = CascadeType.ALL)
	@JoinColumn(name = "tn_product_id")
	private TnProduct tnProduct;
}
