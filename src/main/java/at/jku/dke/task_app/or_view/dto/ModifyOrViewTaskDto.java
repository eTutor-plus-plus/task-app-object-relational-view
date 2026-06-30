package at.jku.dke.task_app.or_view.dto;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * DTO for creating or updating an OR-View task.
 *
 * @param solution the reference SQL solution
 * @param testQuery the test query for result comparison
 * @param underSuperview optional UNDER superview definition
 * @param refSuperview optional MAKE_REF superview definition
 * @param missingPrimitiveFieldPenalty penalty per missing primitive field
 * @param missingObjectFieldPenalty penalty per missing object field
 * @param missingNestedTablePenalty penalty for missing CAST MULTISET
 * @param wrongNestedTableTypePenalty penalty for wrong collection type
 * @param wrongViewObjectTypePenalty penalty for wrong OF type
 * @param wrongOidPenalty penalty for wrong object identifier
 * @param wrongContentPenalty penalty for wrong content
 * @param wrongColumnOrderPenalty penalty for wrong column order
 * @param wrongSuperviewPenalty penalty for wrong UNDER relationship
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
