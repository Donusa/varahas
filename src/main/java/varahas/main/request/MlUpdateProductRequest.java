package varahas.main.request;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import varahas.main.dto.VariationsDTO;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class MlUpdateProductRequest {
	
	private List<VariationsDTO> variations;
	
	
}
