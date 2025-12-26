package varahas.main.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NextCbteRequestDto {
	private Integer ptoVta;
	private Integer cbteTipo;
}

