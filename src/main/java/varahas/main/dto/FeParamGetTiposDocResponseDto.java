package varahas.main.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FeParamGetTiposDocResponseDto {

	private List<DocTipoDto> tiposDocumentos;

	@Data
	@Builder
	@AllArgsConstructor
	@NoArgsConstructor
	public static class DocTipoDto {
		private Integer id;
		private String desc;
		private String fchDesde;
		private String fchHasta;
	}
}
