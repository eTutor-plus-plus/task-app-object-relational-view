package at.jku.dke.task_app.or_view.services;

import at.jku.dke.etutor.task_app.dto.GradingDto;
import at.jku.dke.etutor.task_app.dto.SubmitSubmissionDto;
import at.jku.dke.etutor.task_app.services.BaseSubmissionService;
import at.jku.dke.task_app.or_view.data.entities.OrViewSubmission;
import at.jku.dke.task_app.or_view.data.entities.OrViewTask;
import at.jku.dke.task_app.or_view.data.repositories.OrViewSubmissionRepository;
import at.jku.dke.task_app.or_view.data.repositories.OrViewTaskRepository;
import at.jku.dke.task_app.or_view.dto.SubmissionDto;
import at.jku.dke.task_app.or_view.evaluation.EvaluationService;
import org.springframework.stereotype.Service;

@Service
public class OrViewSubmissionService extends BaseSubmissionService<OrViewTask, OrViewSubmission, SubmissionDto> {

    private final EvaluationService evaluationService;

    public OrViewSubmissionService(OrViewSubmissionRepository submissionRepository,
                                   OrViewTaskRepository taskRepository,
                                   EvaluationService evaluationService) {
        super(submissionRepository, taskRepository);
        this.evaluationService = evaluationService;
    }

    @Override
    protected OrViewSubmission createSubmissionEntity(SubmitSubmissionDto<SubmissionDto> dto) {
        return new OrViewSubmission(dto.submission().input());
    }

    @Override
    protected GradingDto evaluate(SubmitSubmissionDto<SubmissionDto> dto) {
        return this.evaluationService.evaluate(dto);
    }

    @Override
    protected SubmissionDto mapSubmissionToSubmissionData(OrViewSubmission submission) {
        return new SubmissionDto(submission.getSubmission());
    }
}
