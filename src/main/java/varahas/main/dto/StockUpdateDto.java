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
public class StockUpdateDto {

    private Long productId;
    private int totalStock;
    private List<VariationStockDto> variations;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class VariationStockDto {
        private Long variationId;
        private String name;
        private Integer stock;
        private Long meliId;
        private String tnId;
    }
    
}
