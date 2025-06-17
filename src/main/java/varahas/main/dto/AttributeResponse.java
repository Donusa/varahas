package varahas.main.dto;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttributeResponse {
	private String id;
    private String name;
    private String value_type;
    private String type;
    private Map<String, Object> tags;
    private List<AttributeValue> values;
    private String attribute_group_id;
    private String attribute_group_name;

}
