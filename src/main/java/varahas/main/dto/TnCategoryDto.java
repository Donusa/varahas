package varahas.main.dto;

import java.util.Map;

import lombok.Data;

@Data
public class TnCategoryDto {

	private Map<String, String> name;
	private Map<String, String> description;
	private Map<String, String> handle;
	private Object parent;
	private String google_shopping_category;
	private String seo_title;
	private String seo_description;
}
