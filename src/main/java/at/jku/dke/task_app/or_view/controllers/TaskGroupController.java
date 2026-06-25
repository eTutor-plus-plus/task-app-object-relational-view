package at.jku.dke.task_app.or_view.controllers;

import at.jku.dke.etutor.task_app.controllers.BaseTaskGroupController;
import at.jku.dke.task_app.or_view.data.entities.OrViewTaskGroup;
import at.jku.dke.task_app.or_view.dto.ModifyOrViewTaskGroupDto;
import at.jku.dke.task_app.or_view.dto.TaskGroupDto;
import at.jku.dke.task_app.or_view.services.OrViewTaskGroupService;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing OR-View task groups.
 */
@RestController
public class TaskGroupController
    extends BaseTaskGroupController<OrViewTaskGroup, TaskGroupDto, ModifyOrViewTaskGroupDto> {

    public TaskGroupController(OrViewTaskGroupService taskGroupService) {
        super(taskGroupService);
    }

    @Override
    protected TaskGroupDto mapToDto(OrViewTaskGroup taskGroup) {
        return new TaskGroupDto(
            taskGroup.getIntensionalSchema(),
            taskGroup.getExtensionalSchema(),
            taskGroup.getSubmitInserts(),
            taskGroup.getDiagnoseInserts()
        );
    }
}
