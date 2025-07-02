package varahas.main.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeliAttributeCombination {

	private String id;
    private String name;

    @JsonProperty("value_id")
    private String valueId;

    @JsonProperty("value_name")
    private String valueName;
}
