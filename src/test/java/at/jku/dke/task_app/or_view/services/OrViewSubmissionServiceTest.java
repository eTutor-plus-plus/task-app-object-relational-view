package at.jku.dke.task_app.or_view.services;

import at.jku.dke.etutor.task_app.dto.SubmissionMode;
import at.jku.dke.etutor.task_app.dto.SubmitSubmissionDto;
import at.jku.dke.etutor.task_app.dto.GradingDto;
import at.jku.dke.task_app.or_view.data.entities.OrViewSubmission;
import at.jku.dke.task_app.or_view.dto.SubmissionDto;
import at.jku.dke.task_app.or_view.evaluation.EvaluationService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrViewSubmissionServiceTest {

    @Test
    void createSubmissionEntity_shouldCreateCorrectEntity() {

        SubmitSubmissionDto<SubmissionDto> dto =
            new SubmitSubmissionDto<>(
                "user",
                "quiz",
                1L,
                "de",
                SubmissionMode.SUBMIT,
                0,
                new SubmissionDto("INSERT INTO test")
            );

        OrViewSubmissionService service =
            new OrViewSubmissionService(null, null, null);

        OrViewSubmission entity = service.createSubmissionEntity(dto);

        assertEquals("INSERT INTO test", entity.getSubmission());
    }

    @Test
    void mapSubmissionToSubmissionData_shouldMapCorrectly() {

        // Arrange
        OrViewSubmission submission =
            new OrViewSubmission("SELECT 1");

        OrViewSubmissionService service =
            new OrViewSubmissionService(null, null, null);

        SubmissionDto dto =
            service.mapSubmissionToSubmissionData(submission);

        assertEquals("SELECT 1", dto.input());
    }

    @Test
    void evaluate_shouldCallEvaluationService() {

        EvaluationService evaluationService = mock(EvaluationService.class);

        GradingDto grading =
            mock(GradingDto.class);

        SubmitSubmissionDto<SubmissionDto> dto =
            new SubmitSubmissionDto<>(
                "user",
                "quiz",
                1L,
                "de",
                SubmissionMode.SUBMIT,
                0,
                new SubmissionDto("SELECT 1")
            );

        when(evaluationService.evaluate(dto)).thenReturn(grading);

        OrViewSubmissionService service =
            new OrViewSubmissionService(null, null, evaluationService);

        GradingDto result = service.evaluate(dto);

        assertNotNull(result);
        verify(evaluationService).evaluate(dto);
    }
}
