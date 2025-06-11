package varahas.main.dto;

import lombok.Data;

@Data
public class UpdateTnProductStockDto {

	public Long id;
	public Variant[] variants;
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("UpdateTnProductStockDto [id=").append(id).append(", variants=");
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
		public InventoryLevel[] inventory_levels;

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append("Variant [id=").append(id).append(", price=").append(price).append(", inventory_levels=");
			if (inventory_levels != null) {
				for (InventoryLevel level : inventory_levels) {
					sb.append(level.toString()).append(", ");
				}
			}
			sb.append("]");
			return sb.toString();
		}
		
		public static class InventoryLevel {
			public Integer stock;

			@Override
			public String toString() {
				return "InventoryLevel [stock=" + stock + "]";
			}
		}
	}
}
