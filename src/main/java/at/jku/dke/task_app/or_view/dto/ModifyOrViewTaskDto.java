package at.jku.dke.task_app.or_view.dto;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * DTO for creating or updating an OR-View task.
 */
public record ModifyOrViewTaskDto(
    @NotNull String solution,
    @NotNull String testQuery,
    String underSuperview,
    String refSuperview,
    BigDecimal missingPrimitiveFieldPenalty,
    BigDecimal missingObjectFieldPenalty,
    BigDecimal missingNestedTablePenalty,
    BigDecimal wrongNestedTableTypePenalty,
    BigDecimal wrongViewObjectTypePenalty,
    BigDecimal wrongOidPenalty,
    BigDecimal wrongContentPenalty,
    BigDecimal wrongColumnOrderPenalty,
    BigDecimal wrongSuperviewPenalty
) implements Serializable {
}
