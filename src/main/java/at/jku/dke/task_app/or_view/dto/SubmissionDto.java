package at.jku.dke.task_app.or_view.dto;

import jakarta.validation.constraints.NotNull;

public record SubmissionDto(
    @NotNull String input
) {
}
