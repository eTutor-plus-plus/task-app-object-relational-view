package at.jku.dke.task_app.or_view.dto;

import jakarta.validation.constraints.NotNull;

/**
 * DTO for a student submission containing the SQL statement.
 */
public record SubmissionDto(
    @NotNull String input
) {
}
