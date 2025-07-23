package varahas.main.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TnStockUpdateDto {

	private String tnId;
	@JsonProperty("variants")
	private List<TnVariationUpdateDto> variants;
}
