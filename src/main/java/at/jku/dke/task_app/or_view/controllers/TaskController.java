package at.jku.dke.task_app.or_view.controllers;

import at.jku.dke.etutor.task_app.controllers.BaseTaskController;
import at.jku.dke.task_app.or_view.data.entities.OrViewTask;
import at.jku.dke.task_app.or_view.dto.ModifyOrViewTaskDto;
import at.jku.dke.task_app.or_view.dto.TaskDto;
import at.jku.dke.task_app.or_view.services.OrViewTaskService;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * REST controller for managing OR-View tasks.
 */
@RestController
public class TaskController extends BaseTaskController<OrViewTask, TaskDto, ModifyOrViewTaskDto> {

    public TaskController(OrViewTaskService taskService) {
        super(taskService);
    }

    @Override
    protected TaskDto mapToDto(OrViewTask task) {
        return new TaskDto(
            task.getSolution(),
            task.getTestQuery(),
            task.getUnderSuperview(),
            task.getRefSuperview(),
            task.getMissingPrimitiveFieldPenalty(),
            task.getMissingObjectFieldPenalty(),
            task.getMissingNestedTablePenalty(),
            task.getWrongNestedTableTypePenalty(),
            task.getWrongViewObjectTypePenalty(),
            task.getWrongOidPenalty(),
            task.getWrongContentPenalty(),
            task.getWrongColumnOrderPenalty(),
            task.getWrongSuperviewPenalty()
        );
    }

    @Override
    protected URI createDetailsUri(long id) {
        return URI.create("/api/task/or_view/" + id);
    }
}
