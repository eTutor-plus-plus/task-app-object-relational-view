package at.jku.dke.task_app.or_view.dto;

import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

/**
 * DTO for returning OR-View task group data.
 */
public record TaskGroupDto(
    @NotNull String intensionalSchema,
    @NotNull String extensionalSchema,
    @NotNull String submitInserts,
    @NotNull String diagnoseInserts
) implements Serializable {
}
