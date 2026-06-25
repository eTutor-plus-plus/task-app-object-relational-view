package at.jku.dke.task_app.or_view.dto;

import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

/**
 * DTO for creating or updating an OR-View task group.
 */
public record ModifyOrViewTaskGroupDto(
    @NotNull String intensionalSchema,
    @NotNull String extensionalSchema,
    @NotNull String submitInserts,
    @NotNull String diagnoseInserts
) implements Serializable {
}
