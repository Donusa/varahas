package varahas.main.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeCaeSolicitarRequestDto {

	private FeCabReqDto cabecera;
	private List<FeCaeDetRequestDto> detalles;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@Builder
	public static class FeCabReqDto {
		private Integer cantReg;
		private Integer ptoVta;
		private Integer cbteTipo;
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@Builder
	public static class FeCaeDetRequestDto {
		private Integer concepto;
		private Integer docTipo;
		private String docNro;
		private Long cbteDesde;
		private Long cbteHasta;
		private String cbteFch;
		private BigDecimal impTotal;
		private BigDecimal impTotConc;
		private BigDecimal impNeto;
		private BigDecimal impOpEx;
		private BigDecimal impTrib;
		private BigDecimal impIVA;
		private String fchServDesde;
		private String fchServHasta;
		private String fchVtoPago;
		private String monId;
		private BigDecimal monCotiz;
		private Integer condicionIVAReceptorId;
		private List<TributoDto> tributos;
		private List<AlicIvaDto> iva;
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@Builder
	public static class TributoDto {
		private Integer id;
		private String desc;
		private BigDecimal baseImp;
		private BigDecimal alic;
		private BigDecimal importe;
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@Builder
	public static class AlicIvaDto {
		private Integer id;
		private BigDecimal baseImp;
		private BigDecimal importe;
	}
}

