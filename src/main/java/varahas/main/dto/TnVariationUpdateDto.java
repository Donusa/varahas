package varahas.main.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class TnVariationUpdateDto {

	private Long id;
	@JsonProperty("inventory_levels")
	private List<TnInventoryLevelsDto> inventoryLevels;
}
