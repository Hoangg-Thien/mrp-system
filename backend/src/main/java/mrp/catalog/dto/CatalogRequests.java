package mrp.catalog.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public final class CatalogRequests {
    private CatalogRequests() { }
    public record CategoryRequest(@NotBlank @Size(max=150) String name, String description) { }
    public record SupplierRequest(@NotBlank @Size(max=200) String name,
                                  @Size(max=20) String phone, @Email @Size(max=150) String email, String address) { }
    public record ProductRequest(@NotNull @Positive Long categoryId, @NotBlank @Size(max=200) String name,
                                 String description, @NotBlank @Size(max=50) String unit,
                                 @NotNull @DecimalMin("0") @Digits(integer=13, fraction=2) BigDecimal sellingPrice) { }
    public record MaterialRequest(@NotBlank @Size(max=50) String code, @NotBlank @Size(max=200) String name,
                                  @NotBlank @Size(max=50) String unit,
                                  @NotNull @DecimalMin("0") @Digits(integer=13, fraction=2) BigDecimal standardCost) { }
}