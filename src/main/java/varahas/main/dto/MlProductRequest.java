package varahas.main.dto;

import java.util.List;

import lombok.Data;

@Data
public class MlProductRequest {
	private String title;
	private String category_id;
	private Double price;
	private String currency_id;
	private Integer available_quantity;
	private String buying_mode;
	private String condition;
	private String listing_type_id;
	private List<SaleTerms> sale_terms;
	private List<Picture> pictures;
	private List<Attribute> attributes;

	@Data
	public static class SaleTerms {
		private String id;
		private String value_name;
	}

	@Data
	public static class Picture{
		private String source;
	}
	
	@Data
	public static class Attribute{
		private String id;
		private String value_name;
	}
}
