package at.jku.dke.task_app.or_view.controllers;

import at.jku.dke.etutor.task_app.auth.AuthConstants;
import at.jku.dke.etutor.task_app.dto.SubmissionMode;
import at.jku.dke.etutor.task_app.dto.SubmitSubmissionDto;
import at.jku.dke.etutor.task_app.dto.TaskStatus;
import at.jku.dke.task_app.or_view.ClientSetupExtension;
import at.jku.dke.task_app.or_view.DatabaseSetupExtension;
import at.jku.dke.task_app.or_view.data.entities.OrViewTask;
import at.jku.dke.task_app.or_view.data.entities.OrViewTaskGroup;
import at.jku.dke.task_app.or_view.data.repositories.OrViewTaskGroupRepository;
import at.jku.dke.task_app.or_view.data.repositories.OrViewTaskRepository;
import at.jku.dke.task_app.or_view.dto.SubmissionDto;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.math.BigDecimal;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith({DatabaseSetupExtension.class, ClientSetupExtension.class})
class SubmissionControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private OrViewTaskRepository taskRepository;

    @Autowired
    private OrViewTaskGroupRepository groupRepository;

    private long taskId;
    private long penaltyTaskId;

    private static final String NO_SEMICOLON =
        "CREATE OR REPLACE VIEW person_view OF person_ty WITH OBJECT IDENTIFIER (id) AS " +
            "SELECT id, name FROM person";

    private static final String WRONG_KEYWORD =
        "CREATE OR REPLACE VIEW person_view OF person_ty WITH OBJECT IDENTIFIER (id) AS " +
            "SELEC id, name FROM person;";

    private static final String VALID_SOLUTION =
        "CREATE OR REPLACE VIEW person_view OF person_ty WITH OBJECT IDENTIFIER (id) AS " +
            "SELECT id, name FROM person;";

    private static final String WRONG_CONTENT_SOLUTION =
        "CREATE OR REPLACE VIEW person_view OF person_ty WITH OBJECT IDENTIFIER (id) AS " +
            "SELECT id, name FROM person WHERE id = 1;";

    @BeforeEach
    void setup() {
        taskRepository.deleteAllInBatch();
        groupRepository.deleteAllInBatch();

        var group = createGroup();

        var task = new OrViewTask();
        task.setId(1L);
        task.setStatus(TaskStatus.APPROVED);
        task.setTitle("Test Task");
        task.setMaxPoints(BigDecimal.TEN);
        task.setSolution(VALID_SOLUTION);
        task.setTestQuery("SELECT id, name FROM person_view");
        task.setExpectedIdentifier("id");
        task.setTaskGroup(group);
        task = taskRepository.saveAndFlush(task);
        this.taskId = task.getId();

        this.penaltyTaskId = createPenaltyTask(group).getId();
    }

    private OrViewTask createPenaltyTask(OrViewTaskGroup group) {
        var penaltyTask = new OrViewTask();
        penaltyTask.setId(2L);
        penaltyTask.setStatus(TaskStatus.APPROVED);
        penaltyTask.setTitle("Penalty Task");
        penaltyTask.setMaxPoints(BigDecimal.TEN);
        penaltyTask.setSolution(VALID_SOLUTION);
        penaltyTask.setTestQuery("SELECT id, name FROM person_view");
        penaltyTask.setExpectedIdentifier("id");
        penaltyTask.setTaskGroup(group);
        penaltyTask.setMissingPrimitiveFieldPenalty(new BigDecimal("2"));
        penaltyTask.setMissingObjectFieldPenalty(new BigDecimal("3"));
        penaltyTask.setMissingNestedTablePenalty(new BigDecimal("4"));
        penaltyTask.setWrongNestedTableTypePenalty(new BigDecimal("1"));
        penaltyTask.setWrongViewObjectTypePenalty(new BigDecimal("2"));
        penaltyTask.setWrongOidPenalty(new BigDecimal("3"));
        penaltyTask.setWrongContentPenalty(new BigDecimal("2"));
        return taskRepository.saveAndFlush(penaltyTask);
    }

    private OrViewTaskGroup createGroup() {
        var group = new OrViewTaskGroup();
        group.setId(1L);
        group.setStatus(TaskStatus.APPROVED);
        group.setTitle("Test Group");
        group.setIntensionalSchema(
            "CREATE OR REPLACE TYPE person_ty AS OBJECT (id NUMBER, name VARCHAR2(100));"
        );
        group.setExtensionalSchema(
            "CREATE TABLE person (id NUMBER PRIMARY KEY, name VARCHAR2(100));"
        );
        group.setSubmitInserts(
            "INSERT INTO person VALUES (1, 'Alice');\n" +
                "INSERT INTO person VALUES (2, 'Bob');"
        );
        group.setDiagnoseInserts(
            "INSERT INTO person VALUES (1, 'Alice');\n" +
                "INSERT INTO person VALUES (3, 'Charlie');"
        );
        return groupRepository.saveAndFlush(group);
    }

    private SubmitSubmissionDto<SubmissionDto> submission(long taskId, SubmissionMode mode,
                                                          int feedbackLevel, String sql) {
        return new SubmitSubmissionDto<>(
            "test-user", "test-assignment", taskId,
            "de", mode, feedbackLevel,
            new SubmissionDto(sql)
        );
    }

    @Test
    void runValidSolution_alwaysZeroPoints() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.SUBMIT_API_KEY)
            .contentType(ContentType.JSON)
            .body(submission(taskId, SubmissionMode.RUN, 3, VALID_SOLUTION))
            .when()
            .post("/api/submission")
            .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body("grading.maxPoints", equalTo(10))
            .body("grading.points", equalTo(0));
    }

    @Test
    void runValidSolution_showsSyntaxOkAndQueryResult() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.SUBMIT_API_KEY)
            .contentType(ContentType.JSON)
            .body(submission(taskId, SubmissionMode.RUN, 0, VALID_SOLUTION))
            .when()
            .post("/api/submission")
            .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body("grading.points", equalTo(0))
            .body("grading.criteria[0].name", equalTo("Syntax"))
            .body("grading.criteria[0].passed", equalTo(true))
            .body("grading.criteria[1].name", equalTo("Abfrageergebnis"));
    }

    @Test
    void runSyntaxError_showsSyntaxFailed() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.SUBMIT_API_KEY)
            .contentType(ContentType.JSON)
            .body(submission(taskId, SubmissionMode.RUN, 0, NO_SEMICOLON))
            .when()
            .post("/api/submission")
            .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body("grading.points", equalTo(0))
            .body("grading.criteria[0].name", equalTo("Syntax"))
            .body("grading.criteria[0].passed", equalTo(false));
    }

    @Test
    void runWrongKeyword_showsSyntaxFailed() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.SUBMIT_API_KEY)
            .contentType(ContentType.JSON)
            .body(submission(taskId, SubmissionMode.RUN, 3, WRONG_KEYWORD))
            .when()
            .post("/api/submission")
            .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body("grading.points", equalTo(0))
            .body("grading.criteria[0].name", equalTo("Syntax"))
            .body("grading.criteria[0].passed", equalTo(false));
    }

    @Test
    void runFeedbackLevel_isIgnored() {
        int criteriaLevel0 = given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.SUBMIT_API_KEY)
            .contentType(ContentType.JSON)
            .body(submission(taskId, SubmissionMode.RUN, 0, VALID_SOLUTION))
            .when()
            .post("/api/submission")
            .then()
            .statusCode(200)
            .extract().path("grading.criteria.size()");

        int criteriaLevel3 = given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.SUBMIT_API_KEY)
            .contentType(ContentType.JSON)
            .body(submission(taskId, SubmissionMode.RUN, 3, VALID_SOLUTION))
            .when()
            .post("/api/submission")
            .then()
            .statusCode(200)
            .extract().path("grading.criteria.size()");

        assertEquals(criteriaLevel0, criteriaLevel3);
    }

    @Test
    void diagnoseCorrectSolution_returnsFullPoints() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.SUBMIT_API_KEY)
            .contentType(ContentType.JSON)
            .body(submission(taskId, SubmissionMode.DIAGNOSE, 3, VALID_SOLUTION))
            .when()
            .post("/api/submission")
            .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body("grading.maxPoints", equalTo(10))
            .body("grading.points", equalTo(10));
    }

    @Test
    void diagnoseMissingSemicolon_returnsZeroPoints() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.SUBMIT_API_KEY)
            .contentType(ContentType.JSON)
            .body(submission(taskId, SubmissionMode.DIAGNOSE, 3, NO_SEMICOLON))
            .when()
            .post("/api/submission")
            .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body("grading.points", equalTo(0))
            .body("grading.criteria[0].name", equalTo("Syntax"))
            .body("grading.criteria[0].passed", equalTo(false))
            .body("grading.criteria[0].feedback", containsString("Semikolon"));
    }

    @Test
    void diagnoseWrongKeyword_returnsZeroPoints() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.SUBMIT_API_KEY)
            .contentType(ContentType.JSON)
            .body(submission(taskId, SubmissionMode.DIAGNOSE, 3, WRONG_KEYWORD))
            .when()
            .post("/api/submission")
            .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body("grading.points", equalTo(0))
            .body("grading.criteria[0].name", equalTo("Syntax"))
            .body("grading.criteria[0].passed", equalTo(false));
    }

    @Test
    void diagnoseLevel0_showsSyntaxAndQueryResult() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.SUBMIT_API_KEY)
            .contentType(ContentType.JSON)
            .body(submission(taskId, SubmissionMode.DIAGNOSE, 0, VALID_SOLUTION))
            .when()
            .post("/api/submission")
            .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body("grading.criteria", hasSize(2))
            .body("grading.criteria[0].name", equalTo("Syntax"))
            .body("grading.criteria[1].name", equalTo("Abfrageergebnis"));
    }

    @Test
    void diagnoseLevel1_wrongContent_showsErrorType() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.SUBMIT_API_KEY)
            .contentType(ContentType.JSON)
            .body(submission(penaltyTaskId, SubmissionMode.DIAGNOSE, 1, WRONG_CONTENT_SOLUTION))
            .when()
            .post("/api/submission")
            .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body("grading.points", lessThan(10))
            .body("grading.criteria.size()", greaterThan(2));
    }

    @Test
    void diagnoseLevel2_wrongContent_showsErrorTypeAndLocation() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.SUBMIT_API_KEY)
            .contentType(ContentType.JSON)
            .body(submission(penaltyTaskId, SubmissionMode.DIAGNOSE, 2, WRONG_CONTENT_SOLUTION))
            .when()
            .post("/api/submission")
            .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body("grading.points", lessThan(10))
            .body("grading.criteria.size()", greaterThan(2));
    }

    @Test
    void diagnoseLevel3_wrongContent_showsDetailedFeedbackWithPenalties() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.SUBMIT_API_KEY)
            .contentType(ContentType.JSON)
            .body(submission(penaltyTaskId, SubmissionMode.DIAGNOSE, 3, WRONG_CONTENT_SOLUTION))
            .when()
            .post("/api/submission")
            .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body("grading.maxPoints", equalTo(10))
            .body("grading.points", lessThan(10))
            .body("grading.points", greaterThanOrEqualTo(0))
            .body("grading.criteria.size()", greaterThan(2));
    }

    @Test
    void diagnoseLevel0_syntaxError_showsSyntaxOnly() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.SUBMIT_API_KEY)
            .contentType(ContentType.JSON)
            .body(submission(taskId, SubmissionMode.DIAGNOSE, 0, NO_SEMICOLON))
            .when()
            .post("/api/submission")
            .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body("grading.points", equalTo(0))
            .body("grading.criteria[0].name", equalTo("Syntax"))
            .body("grading.criteria[0].passed", equalTo(false));
    }

    @Test
    void diagnoseWrongContent_withPenalties_returnsPartialPoints() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.SUBMIT_API_KEY)
            .contentType(ContentType.JSON)
            .body(submission(penaltyTaskId, SubmissionMode.DIAGNOSE, 3, WRONG_CONTENT_SOLUTION))
            .when()
            .post("/api/submission")
            .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body("grading.maxPoints", equalTo(10))
            .body("grading.points", greaterThan(0))
            .body("grading.points", lessThan(10));
    }

    @Test
    void diagnoseWrongContent_withoutPenalties_returnsFullPoints() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.SUBMIT_API_KEY)
            .contentType(ContentType.JSON)
            .body(submission(taskId, SubmissionMode.DIAGNOSE, 3, WRONG_CONTENT_SOLUTION))
            .when()
            .post("/api/submission")
            .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body("grading.maxPoints", equalTo(10))
            .body("grading.points", equalTo(10));
    }

    @Test
    void submitWrongContent_withoutPenalties_returnsFullPoints() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.SUBMIT_API_KEY)
            .contentType(ContentType.JSON)
            .body(submission(taskId, SubmissionMode.SUBMIT, 3, WRONG_CONTENT_SOLUTION))
            .when()
            .post("/api/submission")
            .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body("grading.maxPoints", equalTo(10))
            .body("grading.points", equalTo(10));
    }

    @Test
    void diagnoseSyntaxError_withPenalties_stillReturnsZeroPoints() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.SUBMIT_API_KEY)
            .contentType(ContentType.JSON)
            .body(submission(penaltyTaskId, SubmissionMode.DIAGNOSE, 3, NO_SEMICOLON))
            .when()
            .post("/api/submission")
            .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body("grading.points", equalTo(0));
    }

    @Test
    void submitCorrectSolution_returnsFullPoints() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.SUBMIT_API_KEY)
            .contentType(ContentType.JSON)
            .body(submission(taskId, SubmissionMode.SUBMIT, 3, VALID_SOLUTION))
            .when()
            .post("/api/submission")
            .then()
            .log().ifValidationFails()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("grading.maxPoints", equalTo(10))
            .body("grading.points", equalTo(10))
            .body("grading.generalFeedback", any(String.class));
    }

    @Test
    void submitMissingSemicolon_returnsZeroPoints() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.SUBMIT_API_KEY)
            .contentType(ContentType.JSON)
            .body(submission(taskId, SubmissionMode.SUBMIT, 3, NO_SEMICOLON))
            .when()
            .post("/api/submission")
            .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body("grading.points", equalTo(0))
            .body("grading.criteria[0].name", equalTo("Syntax"))
            .body("grading.criteria[0].passed", equalTo(false))
            .body("grading.criteria[0].feedback", containsString("Semikolon"));
    }

    @Test
    void submitWrongKeyword_returnsZeroPoints() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.SUBMIT_API_KEY)
            .contentType(ContentType.JSON)
            .body(submission(taskId, SubmissionMode.SUBMIT, 3, WRONG_KEYWORD))
            .when()
            .post("/api/submission")
            .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body("grading.points", equalTo(0))
            .body("grading.criteria[0].name", equalTo("Syntax"))
            .body("grading.criteria[0].passed", equalTo(false));
    }

    @Test
    void submitLevel0_showsSyntaxAndQueryResult() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.SUBMIT_API_KEY)
            .contentType(ContentType.JSON)
            .body(submission(taskId, SubmissionMode.SUBMIT, 0, VALID_SOLUTION))
            .when()
            .post("/api/submission")
            .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body("grading.criteria", hasSize(2))
            .body("grading.criteria[0].name", equalTo("Syntax"))
            .body("grading.criteria[1].name", equalTo("Abfrageergebnis"));
    }

    @Test
    void submitLevel1_wrongContent_showsErrorType() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.SUBMIT_API_KEY)
            .contentType(ContentType.JSON)
            .body(submission(penaltyTaskId, SubmissionMode.SUBMIT, 1, WRONG_CONTENT_SOLUTION))
            .when()
            .post("/api/submission")
            .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body("grading.points", lessThan(10))
            .body("grading.criteria.size()", greaterThan(2));
    }

    @Test
    void submitLevel2_wrongContent_showsErrorTypeAndLocation() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.SUBMIT_API_KEY)
            .contentType(ContentType.JSON)
            .body(submission(penaltyTaskId, SubmissionMode.SUBMIT, 2, WRONG_CONTENT_SOLUTION))
            .when()
            .post("/api/submission")
            .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body("grading.points", lessThan(10))
            .body("grading.criteria.size()", greaterThan(2));
    }

    @Test
    void submitLevel3_wrongContent_showsDetailedFeedbackWithPenalties() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.SUBMIT_API_KEY)
            .contentType(ContentType.JSON)
            .body(submission(penaltyTaskId, SubmissionMode.SUBMIT, 3, WRONG_CONTENT_SOLUTION))
            .when()
            .post("/api/submission")
            .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body("grading.maxPoints", equalTo(10))
            .body("grading.points", lessThan(10))
            .body("grading.points", greaterThanOrEqualTo(0))
            .body("grading.criteria.size()", greaterThan(2));
    }

    @Test
    void submitLevel0_syntaxError_showsSyntaxOnly() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.SUBMIT_API_KEY)
            .contentType(ContentType.JSON)
            .body(submission(taskId, SubmissionMode.SUBMIT, 0, NO_SEMICOLON))
            .when()
            .post("/api/submission")
            .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body("grading.points", equalTo(0))
            .body("grading.criteria[0].name", equalTo("Syntax"))
            .body("grading.criteria[0].passed", equalTo(false));
    }

    @Test
    void submitWrongContent_withPenalties_returnsPartialPoints() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.SUBMIT_API_KEY)
            .contentType(ContentType.JSON)
            .body(submission(penaltyTaskId, SubmissionMode.SUBMIT, 3, WRONG_CONTENT_SOLUTION))
            .when()
            .post("/api/submission")
            .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body("grading.maxPoints", equalTo(10))
            .body("grading.points", greaterThan(0))
            .body("grading.points", lessThan(10));
    }

    @Test
    void submitSyntaxError_withPenalties_stillReturnsZeroPoints() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.SUBMIT_API_KEY)
            .contentType(ContentType.JSON)
            .body(submission(penaltyTaskId, SubmissionMode.SUBMIT, 3, NO_SEMICOLON))
            .when()
            .post("/api/submission")
            .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body("grading.points", equalTo(0));
    }

    @Test
    void submitInBackground_returnsAccepted() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.SUBMIT_API_KEY)
            .contentType(ContentType.JSON)
            .queryParam("runInBackground", true)
            .body(submission(taskId, SubmissionMode.SUBMIT, 3, VALID_SOLUTION))
            .when()
            .post("/api/submission")
            .then()
            .log().ifValidationFails()
            .statusCode(202)
            .contentType(ContentType.TEXT)
            .header("Location", containsString("/api/submission/"))
            .body(any(String.class))
            .body(hasLength(36));
    }

    @Test
    void submitInvalidTaskId_returnsBadRequest() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.SUBMIT_API_KEY)
            .contentType(ContentType.JSON)
            .body(submission(taskId + 999, SubmissionMode.SUBMIT, 3, VALID_SOLUTION))
            .when()
            .post("/api/submission")
            .then()
            .log().ifValidationFails()
            .statusCode(400);
    }

    @Test
    void submitEmptyBody_returnsBadRequest() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.SUBMIT_API_KEY)
            .contentType(ContentType.JSON)
            .when()
            .post("/api/submission")
            .then()
            .log().ifValidationFails()
            .statusCode(400);
    }

    @Test
    void submitWithWrongApiKey_returnsForbidden() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.CRUD_API_KEY)
            .contentType(ContentType.JSON)
            .body(submission(taskId, SubmissionMode.SUBMIT, 3, VALID_SOLUTION))
            .when()
            .post("/api/submission")
            .then()
            .log().ifValidationFails()
            .statusCode(403);
    }

    @Test
    void submitAndDiagnose_useDifferentDatasets() {
        String submitResult = given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.SUBMIT_API_KEY)
            .contentType(ContentType.JSON)
            .body(submission(taskId, SubmissionMode.SUBMIT, 0, VALID_SOLUTION))
            .when()
            .post("/api/submission")
            .then()
            .statusCode(200)
            .extract().path("grading.criteria[1].feedback");

        String diagnoseResult = given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.SUBMIT_API_KEY)
            .contentType(ContentType.JSON)
            .body(submission(taskId, SubmissionMode.DIAGNOSE, 0, VALID_SOLUTION))
            .when()
            .post("/api/submission")
            .then()
            .statusCode(200)
            .extract().path("grading.criteria[1].feedback");

        org.junit.jupiter.api.Assertions.assertNotEquals(submitResult, diagnoseResult);
    }
}
