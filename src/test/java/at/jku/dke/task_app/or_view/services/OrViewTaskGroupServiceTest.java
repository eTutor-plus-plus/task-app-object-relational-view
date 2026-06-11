package at.jku.dke.task_app.or_view.services;

import at.jku.dke.etutor.task_app.dto.ModifyTaskGroupDto;
import at.jku.dke.etutor.task_app.dto.TaskStatus;
import at.jku.dke.task_app.or_view.data.entities.OrViewTaskGroup;
import at.jku.dke.task_app.or_view.data.repositories.OrViewTaskGroupRepository;
import at.jku.dke.task_app.or_view.dto.ModifyOrViewTaskGroupDto;
import at.jku.dke.task_app.or_view.evaluation.OrViewDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrViewTaskGroupServiceTest {

    @Test
    void createTaskGroup_success() {

        OrViewTaskGroupRepository repo = mock(OrViewTaskGroupRepository.class);
        OrViewDataSource dataSource = mock(OrViewDataSource.class);
        MessageSource messageSource = mock(MessageSource.class);

        OrViewTaskGroupService service = new OrViewTaskGroupService(repo, dataSource, messageSource);

        ModifyOrViewTaskGroupDto additional =
            new ModifyOrViewTaskGroupDto(
                "CREATE VIEW v AS SELECT 1;",
                "DROP VIEW v;",
                "INSERT INTO t VALUES (1);",
                "INSERT INTO t VALUES (2);"
            );

        ModifyTaskGroupDto<ModifyOrViewTaskGroupDto> dto =
            new ModifyTaskGroupDto<>("orv", TaskStatus.DRAFT, additional);

        OrViewTaskGroup result = service.createTaskGroup(1L, dto);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(TaskStatus.DRAFT, result.getStatus());

        assertEquals(additional.intensionalSchema(), result.getIntensionalSchema());
        assertEquals(additional.extensionalSchema(), result.getExtensionalSchema());
        assertEquals(additional.submitInserts(), result.getSubmitInserts());
        assertEquals(additional.diagnoseInserts(), result.getDiagnoseInserts());
    }

    @Test
    void updateTaskGroup_success() {

        OrViewTaskGroupRepository repo = mock(OrViewTaskGroupRepository.class);
        OrViewDataSource dataSource = mock(OrViewDataSource.class);
        MessageSource messageSource = mock(MessageSource.class);

        OrViewTaskGroupService service = new OrViewTaskGroupService(repo, dataSource, messageSource);

        OrViewTaskGroup group = new OrViewTaskGroup();

        ModifyOrViewTaskGroupDto additional =
            new ModifyOrViewTaskGroupDto(
                "intensional",
                "extensional",
                "submit",
                "diagnose"
            );

        ModifyTaskGroupDto<ModifyOrViewTaskGroupDto> dto =
            new ModifyTaskGroupDto<>("orv", TaskStatus.DRAFT, additional);

        service.updateTaskGroup(group, dto);

        assertEquals("intensional", group.getIntensionalSchema());
        assertEquals("extensional", group.getExtensionalSchema());
        assertEquals("submit", group.getSubmitInserts());
        assertEquals("diagnose", group.getDiagnoseInserts());
    }

    @Test
    void mapToReturnData() {

        OrViewTaskGroupRepository repo = mock(OrViewTaskGroupRepository.class);
        OrViewDataSource dataSource = mock(OrViewDataSource.class);
        MessageSource messageSource = mock(MessageSource.class);

        OrViewTaskGroupService service = new OrViewTaskGroupService(repo, dataSource, messageSource);

        OrViewTaskGroup group = new OrViewTaskGroup();

        var result = service.mapToReturnData(group, true);

        assertNotNull(result);
        assertEquals("OR-View TaskGroup", result.descriptionDe());
        assertEquals("OR-View TaskGroup", result.descriptionEn());
    }

    @Test
    void createTaskGroup_statusAlwaysDraft() {
        var repo = mock(OrViewTaskGroupRepository.class);
        var dataSource = mock(OrViewDataSource.class);
        var messageSource = mock(MessageSource.class);
        var service = new OrViewTaskGroupService(repo, dataSource, messageSource);

        var dto = new ModifyTaskGroupDto<>("orv", TaskStatus.APPROVED,
            new ModifyOrViewTaskGroupDto("i", "e", "s", "d"));

        var result = service.createTaskGroup(1L, dto);

        assertEquals(TaskStatus.DRAFT, result.getStatus());
    }
}
