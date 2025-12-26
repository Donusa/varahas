package varahas.main.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NextCbteResponseDto {
	private Integer ptoVta;
	private Integer cbteTipo;
	private Long ultimoAutorizado;
	private Long proximo;
	private String soapResponse;
}

