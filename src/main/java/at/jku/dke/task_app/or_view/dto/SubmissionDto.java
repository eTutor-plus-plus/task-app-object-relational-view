package at.jku.dke.task_app.or_view.dto;

import jakarta.validation.constraints.NotNull;

/**
 * DTO for a student submission containing the SQL statement.
 *
 * @param input the submitted SQL statement
 */
public record SubmissionDto(
    @NotNull String input
) {
}
