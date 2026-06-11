package at.jku.dke.task_app.or_view.data.entities;

import at.jku.dke.etutor.task_app.data.entities.BaseSubmission;
import at.jku.dke.etutor.task_app.dto.SubmissionMode;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "submission")
@AttributeOverride(name = "mode", column = @Column(name = "submission_mode", nullable = false))
public class OrViewSubmission extends BaseSubmission<OrViewTask> {

    @Lob
    @NotNull
    @Column(name = "submission", nullable = false)
    private String submission;

    public OrViewSubmission() {}

    public OrViewSubmission(String submission) {
        this.submission = submission;
    }

    public OrViewSubmission(String userId,
                            String assignmentId,
                            OrViewTask task,
                            String language,
                            int feedbackLevel,
                            SubmissionMode mode,
                            String submission) {
        super(userId, assignmentId, task, language, feedbackLevel, mode);
        this.submission = submission;
    }

    public String getSubmission() {
        return submission;
    }

    public void setSubmission(String submission) {
        this.submission = submission;
    }
}
