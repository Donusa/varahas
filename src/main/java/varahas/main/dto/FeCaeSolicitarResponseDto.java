package varahas.main.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeCaeSolicitarResponseDto {

	private String resultado;
	private String reproceso;
	private Integer ptoVta;
	private Integer cbteTipo;
	private List<FeCaeDetResponseDto> detalles;
	private List<MessageDto> errores;
	private String soapResponse;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@Builder
	public static class FeCaeDetResponseDto {
		private Long cbteDesde;
		private Long cbteHasta;
		private String resultado;
		private String cae;
		private String caeFchVto;
		private List<MessageDto> observaciones;
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@Builder
	public static class MessageDto {
		private String code;
		private String msg;
	}
}

