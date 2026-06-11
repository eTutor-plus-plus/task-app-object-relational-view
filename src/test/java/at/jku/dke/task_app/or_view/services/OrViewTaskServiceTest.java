package at.jku.dke.task_app.or_view.services;

import at.jku.dke.etutor.task_app.dto.ModifyTaskDto;
import at.jku.dke.etutor.task_app.dto.TaskStatus;
import at.jku.dke.task_app.or_view.data.entities.OrViewTask;
import at.jku.dke.task_app.or_view.data.entities.OrViewTaskGroup;
import at.jku.dke.task_app.or_view.data.repositories.OrViewTaskGroupRepository;
import at.jku.dke.task_app.or_view.data.repositories.OrViewTaskRepository;
import at.jku.dke.task_app.or_view.dto.ModifyOrViewTaskDto;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrViewTaskServiceTest {

    private static final String VALID_SOLUTION =
        "CREATE OR REPLACE VIEW person_view OF person_ty WITH OBJECT IDENTIFIER (id) AS " +
            "SELECT id, name FROM person;";

    private static ModifyOrViewTaskDto dto(String solution, String testQuery,
                                           String underSuperview, String refSuperview) {
        return new ModifyOrViewTaskDto(solution, testQuery, underSuperview, refSuperview,
            null, null, null, null, null, null, null, null, null);
    }

    private OrViewTaskService createService(OrViewTaskGroup group) {
        var taskRepo = mock(OrViewTaskRepository.class);
        var groupRepo = mock(OrViewTaskGroupRepository.class);
        var messageSource = mock(MessageSource.class);
        when(messageSource.getMessage(anyString(), any(), any())).thenAnswer(i -> i.getArgument(0));
        if (group != null) {
            when(groupRepo.findById(anyLong())).thenReturn(Optional.of(group));
        }
        return new OrViewTaskService(taskRepo, groupRepo, messageSource);
    }

    @Test
    void createTask_shouldSetAllFields() {
        var group = new OrViewTaskGroup();
        group.setId(1L);
        var service = createService(group);
        var modifyDto = dto(VALID_SOLUTION, "SELECT * FROM person_view", null, null);
        var taskDto = new ModifyTaskDto<>(1L, BigDecimal.TEN, "or_view", TaskStatus.DRAFT, modifyDto);

        var result = service.createTask(5L, taskDto);

        assertNotNull(result);
        assertEquals(5L, result.getId());
        assertEquals(VALID_SOLUTION, result.getSolution());
        assertEquals("SELECT * FROM person_view", result.getTestQuery());
        assertEquals("id", result.getExpectedIdentifier());
    }

    @Test
    void createTask_missingSemicolon_shouldThrow() {
        var group = new OrViewTaskGroup();
        group.setId(1L);
        var service = createService(group);
        String noSemicolon =
            "CREATE OR REPLACE VIEW person_view OF person_ty WITH OBJECT IDENTIFIER (id) AS " +
                "SELECT id, name FROM person";
        var taskDto = new ModifyTaskDto<>(1L, BigDecimal.TEN, "or_view", TaskStatus.DRAFT,
            dto(noSemicolon, "SELECT * FROM person_view", null, null));

        assertThrows(ResponseStatusException.class, () -> service.createTask(5L, taskDto));
    }

    @Test
    void createTask_missingOid_shouldThrow() {
        var group = new OrViewTaskGroup();
        group.setId(1L);
        var service = createService(group);
        String noOid =
            "CREATE OR REPLACE VIEW person_view OF person_ty AS " +
                "SELECT id, name FROM person;";
        var taskDto = new ModifyTaskDto<>(1L, BigDecimal.TEN, "or_view", TaskStatus.DRAFT,
            dto(noOid, "SELECT * FROM person_view", null, null));

        assertThrows(ResponseStatusException.class, () -> service.createTask(5L, taskDto));
    }

    @Test
    void createTask_underWithoutSuperview_shouldThrow() {
        var group = new OrViewTaskGroup();
        group.setId(1L);
        var service = createService(group);
        String underWithoutSuperview =
            "CREATE OR REPLACE VIEW sub_view OF sub_ty UNDER super_view AS " +
                "SELECT id FROM person;";
        var taskDto = new ModifyTaskDto<>(1L, BigDecimal.TEN, "or_view", TaskStatus.DRAFT,
            dto(underWithoutSuperview, "SELECT * FROM sub_view", null, null));

        assertThrows(ResponseStatusException.class, () -> service.createTask(5L, taskDto));
    }

    @Test
    void createTask_superviewWithoutUnder_shouldThrow() {
        var group = new OrViewTaskGroup();
        group.setId(1L);
        var service = createService(group);
        var taskDto = new ModifyTaskDto<>(1L, BigDecimal.TEN, "or_view", TaskStatus.DRAFT,
            dto(VALID_SOLUTION, "SELECT * FROM person_view",
                "CREATE OR REPLACE VIEW super_view OF super_ty AS SELECT id FROM t;",
                null));

        assertThrows(ResponseStatusException.class, () -> service.createTask(5L, taskDto));
    }

    @Test
    void createTask_withValidSuperview_shouldSucceed() {
        var group = new OrViewTaskGroup();
        group.setId(1L);
        var service = createService(group);
        String underSolution =
            "CREATE OR REPLACE VIEW sub_view OF sub_ty UNDER super_view AS " +
                "SELECT id FROM person;";
        var taskDto = new ModifyTaskDto<>(1L, BigDecimal.TEN, "or_view", TaskStatus.DRAFT,
            dto(underSolution, "SELECT * FROM sub_view",
                "CREATE OR REPLACE VIEW super_view OF super_ty AS SELECT id FROM t;",
                null));

        var result = service.createTask(5L, taskDto);

        assertNotNull(result);
        assertEquals(underSolution, result.getSolution());
        assertNull(result.getExpectedIdentifier());
    }

    @Test
    void createTask_withPenalties_shouldSavePenalties() {
        var group = new OrViewTaskGroup();
        group.setId(1L);
        var service = createService(group);
        var modifyDto = new ModifyOrViewTaskDto(
            VALID_SOLUTION, "SELECT * FROM person_view", null, null,
            new BigDecimal("2"), new BigDecimal("3"), new BigDecimal("4"),
            new BigDecimal("1"), new BigDecimal("2"), new BigDecimal("3"), new BigDecimal("4"), new BigDecimal("5"), new BigDecimal("6")
        );
        var taskDto = new ModifyTaskDto<>(1L, BigDecimal.TEN, "or_view", TaskStatus.DRAFT, modifyDto);

        var result = service.createTask(5L, taskDto);

        assertEquals(new BigDecimal("2"), result.getMissingPrimitiveFieldPenalty());
        assertEquals(new BigDecimal("3"), result.getMissingObjectFieldPenalty());
        assertEquals(new BigDecimal("4"), result.getMissingNestedTablePenalty());
        assertEquals(new BigDecimal("1"), result.getWrongNestedTableTypePenalty());
        assertEquals(new BigDecimal("2"), result.getWrongViewObjectTypePenalty());
    }

    @Test
    void createTask_withNullPenalties_shouldSaveNulls() {
        var group = new OrViewTaskGroup();
        group.setId(1L);
        var service = createService(group);
        var taskDto = new ModifyTaskDto<>(1L, BigDecimal.TEN, "or_view", TaskStatus.DRAFT,
            dto(VALID_SOLUTION, "SELECT * FROM person_view", null, null));

        var result = service.createTask(5L, taskDto);

        assertNull(result.getMissingPrimitiveFieldPenalty());
        assertNull(result.getMissingObjectFieldPenalty());
        assertNull(result.getMissingNestedTablePenalty());
        assertNull(result.getWrongNestedTableTypePenalty());
        assertNull(result.getWrongViewObjectTypePenalty());
    }

    @Test
    void updateTask_shouldUpdateFields() {
        var group = new OrViewTaskGroup();
        group.setId(1L);
        var service = createService(group);
        var task = new OrViewTask();
        task.setSolution("old solution;");
        task.setTestQuery("old query");

        String newSolution =
            "CREATE OR REPLACE VIEW emp_view OF emp_ty WITH OBJECT IDENTIFIER (id) AS " +
                "SELECT id FROM emp;";
        var taskDto = new ModifyTaskDto<>(1L, BigDecimal.TEN, "or_view", TaskStatus.DRAFT,
            dto(newSolution, "SELECT * FROM emp_view", null, null));

        service.updateTask(task, taskDto);

        assertEquals(newSolution, task.getSolution());
        assertEquals("SELECT * FROM emp_view", task.getTestQuery());
        assertEquals("id", task.getExpectedIdentifier());
    }

    @Test
    void mapToReturnData_shouldReturnNotNull() {
        var service = createService(null);
        var task = new OrViewTask();

        var result = service.mapToReturnData(task, true);

        assertNotNull(result);
    }
}
