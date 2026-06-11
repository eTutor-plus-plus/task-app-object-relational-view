package at.jku.dke.task_app.or_view.data;

import at.jku.dke.etutor.task_app.dto.SubmissionMode;
import at.jku.dke.task_app.or_view.data.entities.OrViewSubmission;
import at.jku.dke.task_app.or_view.data.entities.OrViewTask;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;

class OrViewSubmissionTest {

    @Test
    void testConstructor1() {

        var expected = "test-submission";

        var submission = new OrViewSubmission(expected);
        var actual = submission.getSubmission();

        assertEquals(expected, actual);
    }

    @Test
    void testConstructor2() {

        var user = "test-user";
        var assignment = "a1";
        var task = new OrViewTask();
        var lang = "de";
        var feedbackLevel = 2;
        var mode = SubmissionMode.SUBMIT;
        var input = "select * from table";

        var submission = new OrViewSubmission(
            user,
            assignment,
            task,
            lang,
            feedbackLevel,
            mode,
            input
        );

        assertEquals(user, submission.getUserId());
        assertEquals(assignment, submission.getAssignmentId());
        assertEquals(task, submission.getTask());
        assertEquals(lang, submission.getLanguage());
        assertEquals(feedbackLevel, submission.getFeedbackLevel());
        assertEquals(mode, submission.getMode());
        assertEquals(input, submission.getSubmission());
    }

    @Test
    void testGetSetSubmission() {

        var submission = new OrViewSubmission();
        var expected = "new-submission";

        submission.setSubmission(expected);
        var actual = submission.getSubmission();

        assertEquals(expected, actual);
    }

    @Test
    void testGetSetMode() {

        var submission = new OrViewSubmission();
        var expected = SubmissionMode.SUBMIT;

        submission.setMode(expected);
        var actual = submission.getMode();

        assertEquals(expected, actual);
    }
}
