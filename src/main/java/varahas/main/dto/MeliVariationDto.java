package varahas.main.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeliVariationDto {
	
	private Long id;
	private Double price;

	@JsonProperty("attribute_combinations")
	private List<MeliAttributeCombination> attributeCombinations;

	@JsonProperty("available_quantity")
	private Integer availableQuantity;

	@JsonProperty("sold_quantity")
	private Integer soldQuantity;

	@JsonProperty("picture_ids")
	private List<String> pictureIds;

	@JsonProperty("user_product_id")
	private String userProductId;
}
