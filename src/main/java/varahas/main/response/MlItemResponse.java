package varahas.main.response;

import java.util.Map;

import lombok.Data;

@Data
public class MlItemResponse {

	private String id;
    private String title;
    private Double price;
    private Integer available_quantity;
    private Map<String, Object> shipping;
    private String status;
}
