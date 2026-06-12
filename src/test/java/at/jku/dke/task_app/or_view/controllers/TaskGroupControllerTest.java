package at.jku.dke.task_app.or_view.controllers;

import at.jku.dke.etutor.task_app.auth.AuthConstants;
import at.jku.dke.etutor.task_app.dto.ModifyTaskGroupDto;
import at.jku.dke.etutor.task_app.dto.TaskStatus;
import at.jku.dke.task_app.or_view.ClientSetupExtension;
import at.jku.dke.task_app.or_view.DatabaseSetupExtension;
import at.jku.dke.task_app.or_view.data.entities.OrViewTaskGroup;
import at.jku.dke.task_app.or_view.data.repositories.OrViewTaskGroupRepository;
import at.jku.dke.task_app.or_view.dto.ModifyOrViewTaskGroupDto;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith({DatabaseSetupExtension.class, ClientSetupExtension.class})
class TaskGroupControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private OrViewTaskGroupRepository groupRepository;

    private long groupId;

    @BeforeEach
    void setup() {
        groupRepository.deleteAllInBatch();

        var group = new OrViewTaskGroup();
        group.setId(1L);
        group.setStatus(TaskStatus.APPROVED);
        group.setTitle("Test Group");
        group.setIntensionalSchema("CREATE TYPE test_ty AS OBJECT (id NUMBER);");
        group.setExtensionalSchema("CREATE TABLE test_table (id NUMBER);");
        group.setSubmitInserts("INSERT INTO test_table VALUES (1);");
        group.setDiagnoseInserts("INSERT INTO test_table VALUES (2);");
        group = groupRepository.saveAndFlush(group);
        this.groupId = group.getId();
    }

    @Test
    void getShouldReturnOk() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.CRUD_API_KEY)
            .accept(ContentType.JSON)
            .when()
            .get("/api/taskGroup/{id}", groupId)
            .then()
            .log().ifValidationFails()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("intensionalSchema", equalTo("CREATE TYPE test_ty AS OBJECT (id NUMBER);"))
            .body("extensionalSchema", equalTo("CREATE TABLE test_table (id NUMBER);"));
    }

    @Test
    void getShouldReturnNotFound() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.CRUD_API_KEY)
            .accept(ContentType.JSON)
            .when()
            .get("/api/taskGroup/{id}", groupId + 999)
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
            .get("/api/taskGroup/{id}", groupId)
            .then()
            .log().ifValidationFails()
            .statusCode(403);
    }

    @Test
    void createShouldReturnCreated() {
        var dto = new ModifyTaskGroupDto<>(
            "or_view",
            TaskStatus.DRAFT,
            new ModifyOrViewTaskGroupDto(
                "CREATE TYPE t AS OBJECT (id NUMBER);",
                "CREATE TABLE tbl (id NUMBER);",
                "INSERT INTO tbl VALUES (1);",
                "INSERT INTO tbl VALUES (2);"
            )
        );

        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.CRUD_API_KEY)
            .contentType(ContentType.JSON)
            .body(dto)
            .when()
            .post("/api/taskGroup/{id}", groupId + 1)
            .then()
            .log().ifValidationFails()
            .statusCode(201);
    }

    @Test
    void createShouldReturnBadRequestOnEmptyBody() {
        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.CRUD_API_KEY)
            .contentType(ContentType.JSON)
            .when()
            .post("/api/taskGroup/{id}", groupId + 2)
            .then()
            .log().ifValidationFails()
            .statusCode(400);
    }

    @Test
    void createShouldReturnForbidden() {
        var dto = new ModifyTaskGroupDto<>(
            "or_view",
            TaskStatus.DRAFT,
            new ModifyOrViewTaskGroupDto(
                "CREATE TYPE t AS OBJECT (id NUMBER);",
                "CREATE TABLE tbl (id NUMBER);",
                "INSERT INTO tbl VALUES (1);",
                "INSERT INTO tbl VALUES (2);"
            )
        );

        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.SUBMIT_API_KEY)
            .contentType(ContentType.JSON)
            .body(dto)
            .when()
            .post("/api/taskGroup/{id}", groupId + 3)
            .then()
            .log().ifValidationFails()
            .statusCode(403);
    }

    @Test
    void updateShouldReturnOk() {
        var dto = new ModifyTaskGroupDto<>(
            "or_view",
            TaskStatus.APPROVED,
            new ModifyOrViewTaskGroupDto(
                "CREATE TYPE t2 AS OBJECT (id NUMBER);",
                "CREATE TABLE tbl2 (id NUMBER);",
                "INSERT INTO tbl2 VALUES (1);",
                "INSERT INTO tbl2 VALUES (2);"
            )
        );

        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.CRUD_API_KEY)
            .contentType(ContentType.JSON)
            .body(dto)
            .when()
            .put("/api/taskGroup/{id}", groupId)
            .then()
            .log().ifValidationFails()
            .statusCode(200);
    }

    @Test
    void updateShouldReturnNotFound() {
        var dto = new ModifyTaskGroupDto<>(
            "or_view",
            TaskStatus.DRAFT,
            new ModifyOrViewTaskGroupDto(
                "CREATE TYPE t AS OBJECT (id NUMBER);",
                "CREATE TABLE tbl (id NUMBER);",
                "INSERT INTO tbl VALUES (1);",
                "INSERT INTO tbl VALUES (2);"
            )
        );

        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.CRUD_API_KEY)
            .contentType(ContentType.JSON)
            .body(dto)
            .when()
            .put("/api/taskGroup/{id}", groupId + 999)
            .then()
            .log().ifValidationFails()
            .statusCode(404);
    }

    @Test
    void updateShouldReturnForbidden() {
        var dto = new ModifyTaskGroupDto<>(
            "or_view",
            TaskStatus.DRAFT,
            new ModifyOrViewTaskGroupDto(
                "CREATE TYPE t AS OBJECT (id NUMBER);",
                "CREATE TABLE tbl (id NUMBER);",
                "INSERT INTO tbl VALUES (1);",
                "INSERT INTO tbl VALUES (2);"
            )
        );

        given()
            .port(port)
            .header(AuthConstants.AUTH_TOKEN_HEADER_NAME, ClientSetupExtension.SUBMIT_API_KEY)
            .contentType(ContentType.JSON)
            .body(dto)
            .when()
            .put("/api/taskGroup/{id}", groupId)
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
            .delete("/api/taskGroup/{id}", groupId)
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
            .delete("/api/taskGroup/{id}", groupId + 999)
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
            .delete("/api/taskGroup/{id}", groupId)
            .then()
            .log().ifValidationFails()
            .statusCode(403);
    }

    @Test
    void mapToDto() {
        var group = new OrViewTaskGroup();
        group.setIntensionalSchema("CREATE TYPE t AS OBJECT (id NUMBER);");
        group.setExtensionalSchema("CREATE TABLE tbl (id NUMBER);");
        group.setSubmitInserts("INSERT INTO tbl VALUES (1);");
        group.setDiagnoseInserts("INSERT INTO tbl VALUES (2);");

        var result = new TaskGroupController(null).mapToDto(group);

        assertEquals("CREATE TYPE t AS OBJECT (id NUMBER);", result.intensionalSchema());
        assertEquals("CREATE TABLE tbl (id NUMBER);", result.extensionalSchema());
        assertEquals("INSERT INTO tbl VALUES (1);", result.submitInserts());
        assertEquals("INSERT INTO tbl VALUES (2);", result.diagnoseInserts());
    }
}
