package com.ecommerce.inventory.dto;

import com.ecommerce.inventory.entity.StockAdjustmentReason;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Data;

@Data
public class StockAdjustmentRequest {
    @NotNull private Integer adjustment;
    @NotNull private StockAdjustmentReason reason;
    private UUID referenceId;
}
