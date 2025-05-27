package varahas.main.entities;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "meli_item")
public class MeliItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	private String title;
	@JsonProperty("category_id")
	private String categoryId;
	private Double price;
	@JsonProperty("currency_id")
	private String currencyId;
	@JsonProperty("available_quantity")
	private Integer availableQuantity;
	@JsonProperty("buying_mode")
    private String buyingMode;
	private String condition;
	@JsonProperty("listing_type_id")
	private String listingTypeId;
	@OneToMany(mappedBy = "meliItem", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<SaleTermsEntity> saleTerms;
	@OneToMany(mappedBy = "meliItem", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<PictureEntity> pictures;
	@OneToMany(mappedBy = "meliItem", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<AttributeEntity> attributes;
}
