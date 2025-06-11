package varahas.main.dto;

import lombok.Data;

@Data
public class UpdatePriceAndStockDto {

	public Long id;
	public Variant[] variants;
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("UpdatePriceAndStockDto [id=").append(id).append(", variants=");
		if (variants != null) {
			for (Variant variant : variants) {
				sb.append(variant.toString()).append(", ");
			}
		}
		sb.append("]");
		return sb.toString();
	}
	
	public static class Variant {
		public Long id;
		public Integer price;
		public Integer stock;

		@Override
		public String toString() {
			return "Variant [id=" + id + ", price=" + price + ", stock=" + stock + "]";
		}
	}
}
