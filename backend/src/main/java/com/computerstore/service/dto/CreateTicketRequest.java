package com.computerstore.service.dto;
import jakarta.validation.constraints.*;
public record CreateTicketRequest(
        @NotBlank @Pattern(regexp = "PlayStation|Notebook|PC de escritorio") String deviceType,
        @Size(max = 100) String brand,
        @Size(max = 150) String model,
        @NotBlank @Size(max = 2000) String reportedProblem
) {
    @AssertTrue(message = "La marca sólo debe informarse y es obligatoria para notebooks.")
    public boolean isBrandValid() {
        return "Notebook".equals(deviceType)
                ? brand != null && !brand.isBlank()
                : brand == null || brand.isBlank();
    }

    @AssertTrue(message = "El modelo no debe informarse para PC de escritorio y es obligatorio para los demás equipos.")
    public boolean isModelValid() {
        return "PC de escritorio".equals(deviceType)
                ? model == null || model.isBlank()
                : model != null && !model.isBlank();
    }
}
