package at.jku.dke.task_app.or_view.controllers;

import at.jku.dke.etutor.task_app.controllers.BaseSubmissionController;
import at.jku.dke.task_app.or_view.dto.SubmissionDto;
import at.jku.dke.task_app.or_view.services.OrViewSubmissionService;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for handling student submissions.
 */
@RestController
public class SubmissionController extends BaseSubmissionController<SubmissionDto> {

    public SubmissionController(OrViewSubmissionService submissionService) {
        super(submissionService);
    }
}
