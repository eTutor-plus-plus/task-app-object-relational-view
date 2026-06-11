package at.jku.dke.task_app.or_view.controllers;

import at.jku.dke.etutor.task_app.auth.AuthConstants;
import at.jku.dke.etutor.task_app.dto.ModifyTaskDto;
import at.jku.dke.etutor.task_app.dto.TaskStatus;
import at.jku.dke.task_app.or_view.ClientSetupExtension;
import at.jku.dke.task_app.or_view.DatabaseSetupExtension;
import at.jku.dke.task_app.or_view.data.entities.OrViewTask;
import at.jku.dke.task_app.or_view.data.entities.OrViewTaskGroup;
import at.jku.dke.task_app.or_view.data.repositories.OrViewTaskGroupRepository;
import at.jku.dke.task_app.or_view.data.repositories.OrViewTaskRepository;
import at.jku.dke.task_app.or_view.dto.ModifyOrViewTaskDto;
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
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith({DatabaseSetupExtension.class, ClientSetupExtension.class})
class TaskControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private OrViewTaskRepository taskRepository;

    @Autowired
    private OrViewTaskGroupRepository groupRepository;

    private long taskId;
    private long groupId;

    private static final String VALID_SOLUTION =
        "CREATE OR REPLACE VIEW test_view OF test_ty WITH OBJECT IDENTIFIER (id) AS " +
            "SELECT id, name FROM test_table;";

    private static ModifyOrViewTaskDto dto(String solution, String testQuery,
                                           String underSuperview, String refSuperview) {
        return new ModifyOrViewTaskDto(solution, testQuery, underSuperview, refSuperview,
            null, null, null, null, null, null, null, null, null);
    }

    @BeforeEach
    void setup() {
        taskRepository.deleteAllInBatch();
        groupRepository.deleteAllInBatch();

        var group = new OrViewTaskGroup();
        group.setId(1L);
        group.setStatus(TaskStatus.DRAFT);
        group.setIntensionalSchema("-- schema");
        group.setExtensionalSchema("-- ext");
        group.setSubmitInserts("-- submit");
        group.setDiagnoseInserts("-- diagnose");
        group.setTitle("Test Group");
        group = groupRepository.saveAndFlush(group);
        this.groupId = group.getId();

        var task = new OrViewTask();
        task.setId(1L);
        task.setStatus(TaskStatus.DRAFT);
        task.setSolution(VALID_SOLUTION);
        task.setTestQuery("SELECT * FROM test_view");
        task.setExpectedIdentifier("id");
        task.setTaskGroup(group);
        task.setMaxPoints(BigDecimal.ONE);
        task.setTitle("Test Task");
        task = taskRepository.saveAndFlush(task);
        this.taskId = task.getId();
    }

    @Test
    void getShouldReturnOk() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.CRUD_API_KEY)
            .accept(ContentType.JSON)
            .when()
            .get("/api/task/{id}", taskId)
            .then()
            .log().ifValidationFails()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("solution", equalTo(VALID_SOLUTION))
            .body("testQuery", equalTo("SELECT * FROM test_view"));
    }

    @Test
    void getShouldReturnNotFound() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.CRUD_API_KEY)
            .accept(ContentType.JSON)
            .when()
            .get("/api/task/{id}", taskId + 999)
            .then()
            .log().ifValidationFails()
            .statusCode(404);
    }

    @Test
    void getShouldReturnForbidden() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.SUBMIT_API_KEY)
            .accept(ContentType.JSON)
            .when()
            .get("/api/task/{id}", taskId)
            .then()
            .log().ifValidationFails()
            .statusCode(403);
    }

    @Test
    void createShouldReturnCreated() {
        var taskDto = new ModifyTaskDto<>(
            groupId, BigDecimal.TEN, "or_view", TaskStatus.DRAFT,
            dto(VALID_SOLUTION, "SELECT * FROM test_view", null, null)
        );

        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.CRUD_API_KEY)
            .contentType(ContentType.JSON)
            .body(taskDto)
            .when()
            .post("/api/task/{id}", taskId + 1)
            .then()
            .log().ifValidationFails()
            .statusCode(201);
    }

    @Test
    void createWithSuperviewShouldReturnCreated() {
        var taskDto = new ModifyTaskDto<>(
            groupId, BigDecimal.TEN, "or_view", TaskStatus.DRAFT,
            dto(
                "CREATE OR REPLACE VIEW sub_view OF sub_ty UNDER super_view AS " +
                    "SELECT id FROM test_table;",
                "SELECT * FROM sub_view",
                "CREATE OR REPLACE VIEW super_view OF super_ty AS SELECT id FROM test_table;",
                null
            )
        );

        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.CRUD_API_KEY)
            .contentType(ContentType.JSON)
            .body(taskDto)
            .when()
            .post("/api/task/{id}", taskId + 2)
            .then()
            .log().ifValidationFails()
            .statusCode(201);
    }

    @Test
    void createShouldReturnBadRequestOnMissingSemicolon() {
        String noSemicolon =
            "CREATE OR REPLACE VIEW test_view OF test_ty WITH OBJECT IDENTIFIER (id) AS " +
                "SELECT id FROM test_table";

        var taskDto = new ModifyTaskDto<>(
            groupId, BigDecimal.ONE, "or_view", TaskStatus.DRAFT,
            dto(noSemicolon, "SELECT * FROM test_view", null, null)
        );

        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.CRUD_API_KEY)
            .contentType(ContentType.JSON)
            .body(taskDto)
            .when()
            .post("/api/task/{id}", taskId + 3)
            .then()
            .log().ifValidationFails()
            .statusCode(400);
    }

    @Test
    void createShouldReturnBadRequestOnEmptyBody() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.CRUD_API_KEY)
            .contentType(ContentType.JSON)
            .when()
            .post("/api/task/{id}", taskId + 4)
            .then()
            .log().ifValidationFails()
            .statusCode(400);
    }

    @Test
    void createShouldReturnForbidden() {
        var taskDto = new ModifyTaskDto<>(
            groupId, BigDecimal.ONE, "or_view", TaskStatus.DRAFT,
            dto(VALID_SOLUTION, "SELECT * FROM test_view", null, null)
        );

        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.SUBMIT_API_KEY)
            .contentType(ContentType.JSON)
            .body(taskDto)
            .when()
            .post("/api/task/{id}", taskId + 5)
            .then()
            .log().ifValidationFails()
            .statusCode(403);
    }

    @Test
    void createWithPenaltiesShouldReturnCreated() {
        var modifyDto = new ModifyOrViewTaskDto(
            VALID_SOLUTION, "SELECT * FROM test_view", null, null,
            new BigDecimal("2"), new BigDecimal("3"), new BigDecimal("4"),
            new BigDecimal("1"), new BigDecimal("2"), new BigDecimal("3"),
            new BigDecimal("4"), new BigDecimal("1.5"), new BigDecimal("2.5")
        );
        var taskDto = new ModifyTaskDto<>(
            groupId, BigDecimal.TEN, "or_view", TaskStatus.DRAFT, modifyDto
        );

        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.CRUD_API_KEY)
            .contentType(ContentType.JSON)
            .body(taskDto)
            .when()
            .post("/api/task/{id}", taskId + 6)
            .then()
            .log().ifValidationFails()
            .statusCode(201);
    }

    @Test
    void updateShouldReturnOk() {
        String updatedSolution =
            "CREATE OR REPLACE VIEW test_view2 OF test_ty WITH OBJECT IDENTIFIER (id) AS " +
                "SELECT id FROM test_table;";

        var taskDto = new ModifyTaskDto<>(
            groupId, BigDecimal.TEN, "or_view", TaskStatus.DRAFT,
            dto(updatedSolution, "SELECT * FROM test_view2", null, null)
        );

        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.CRUD_API_KEY)
            .contentType(ContentType.JSON)
            .body(taskDto)
            .when()
            .put("/api/task/{id}", taskId)
            .then()
            .log().ifValidationFails()
            .statusCode(200);
    }

    @Test
    void updateShouldReturnNotFound() {
        var taskDto = new ModifyTaskDto<>(
            groupId, BigDecimal.TEN, "or_view", TaskStatus.DRAFT,
            dto(VALID_SOLUTION, "SELECT * FROM test_view", null, null)
        );

        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.CRUD_API_KEY)
            .contentType(ContentType.JSON)
            .body(taskDto)
            .when()
            .put("/api/task/{id}", taskId + 999)
            .then()
            .log().ifValidationFails()
            .statusCode(404);
    }

    @Test
    void updateShouldReturnForbidden() {
        var taskDto = new ModifyTaskDto<>(
            groupId, BigDecimal.ONE, "or_view", TaskStatus.DRAFT,
            dto(VALID_SOLUTION, "SELECT * FROM test_view", null, null)
        );

        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.SUBMIT_API_KEY)
            .contentType(ContentType.JSON)
            .body(taskDto)
            .when()
            .put("/api/task/{id}", taskId)
            .then()
            .log().ifValidationFails()
            .statusCode(403);
    }

    @Test
    void deleteShouldReturnNoContent() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.CRUD_API_KEY)
            .when()
            .delete("/api/task/{id}", taskId)
            .then()
            .log().ifValidationFails()
            .statusCode(204);
    }

    @Test
    void deleteShouldReturnNoContentOnNotFound() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.CRUD_API_KEY)
            .when()
            .delete("/api/task/{id}", taskId + 999)
            .then()
            .log().ifValidationFails()
            .statusCode(204);
    }

    @Test
    void deleteShouldReturnForbidden() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.SUBMIT_API_KEY)
            .when()
            .delete("/api/task/{id}", taskId)
            .then()
            .log().ifValidationFails()
            .statusCode(403);
    }

    @Test
    void mapToDto() {
        var task = new OrViewTask();
        task.setSolution(VALID_SOLUTION);
        task.setTestQuery("SELECT * FROM test_view");
        task.setUnderSuperview("CREATE VIEW super_view OF super_ty AS SELECT id FROM t;");
        task.setRefSuperview("CREATE VIEW ref_view OF ref_ty AS SELECT id FROM t;");

        var result = new TaskController(null).mapToDto(task);

        assertEquals(VALID_SOLUTION, result.solution());
        assertEquals("SELECT * FROM test_view", result.testQuery());
        assertEquals("CREATE VIEW super_view OF super_ty AS SELECT id FROM t;", result.underSuperview());
        assertEquals("CREATE VIEW ref_view OF ref_ty AS SELECT id FROM t;", result.refSuperview());
    }

    @Test
    void mapToDtoWithNullSuperviews() {
        var task = new OrViewTask();
        task.setSolution(VALID_SOLUTION);
        task.setTestQuery("SELECT * FROM test_view");
        task.setUnderSuperview(null);
        task.setRefSuperview(null);

        var result = new TaskController(null).mapToDto(task);

        assertEquals(VALID_SOLUTION, result.solution());
        assertEquals("SELECT * FROM test_view", result.testQuery());
        assertNull(result.underSuperview());
        assertNull(result.refSuperview());
    }

    @Test
    void mapToDtoWithPenalties() {
        var task = new OrViewTask();
        task.setSolution(VALID_SOLUTION);
        task.setTestQuery("SELECT * FROM test_view");
        task.setMissingPrimitiveFieldPenalty(new BigDecimal("2"));
        task.setMissingObjectFieldPenalty(new BigDecimal("3"));
        task.setMissingNestedTablePenalty(new BigDecimal("4"));
        task.setWrongNestedTableTypePenalty(new BigDecimal("1"));
        task.setWrongViewObjectTypePenalty(new BigDecimal("2"));
        task.setWrongOidPenalty(new BigDecimal("5"));
        task.setWrongContentPenalty(new BigDecimal("6"));
        task.setWrongColumnOrderPenalty(new BigDecimal("1.5"));
        task.setWrongSuperviewPenalty(new BigDecimal("2.5"));

        var result = new TaskController(null).mapToDto(task);

        assertEquals(new BigDecimal("2"), result.missingPrimitiveFieldPenalty());
        assertEquals(new BigDecimal("3"), result.missingObjectFieldPenalty());
        assertEquals(new BigDecimal("4"), result.missingNestedTablePenalty());
        assertEquals(new BigDecimal("1"), result.wrongNestedTableTypePenalty());
        assertEquals(new BigDecimal("2"), result.wrongViewObjectTypePenalty());
        assertEquals(new BigDecimal("5"), result.wrongOidPenalty());
        assertEquals(new BigDecimal("6"), result.wrongContentPenalty());
        assertEquals(new BigDecimal("1.5"), result.wrongColumnOrderPenalty());
        assertEquals(new BigDecimal("2.5"), result.wrongSuperviewPenalty());
    }

    @Test
    void mapToDtoWithNullPenalties() {
        var task = new OrViewTask();
        task.setSolution(VALID_SOLUTION);
        task.setTestQuery("SELECT * FROM test_view");

        var result = new TaskController(null).mapToDto(task);

        assertNull(result.missingPrimitiveFieldPenalty());
        assertNull(result.missingObjectFieldPenalty());
        assertNull(result.missingNestedTablePenalty());
        assertNull(result.wrongNestedTableTypePenalty());
        assertNull(result.wrongViewObjectTypePenalty());
        assertNull(result.wrongOidPenalty());
        assertNull(result.wrongContentPenalty());
        assertNull(result.wrongColumnOrderPenalty());
        assertNull(result.wrongSuperviewPenalty());
    }
}
