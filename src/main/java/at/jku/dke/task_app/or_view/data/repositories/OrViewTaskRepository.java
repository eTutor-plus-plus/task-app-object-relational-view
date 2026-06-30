package at.jku.dke.task_app.or_view.data.repositories;

import at.jku.dke.etutor.task_app.data.repositories.TaskRepository;
import at.jku.dke.task_app.or_view.data.entities.OrViewTask;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Repository for entity {@link OrViewTask}.
 */
public interface OrViewTaskRepository extends TaskRepository<OrViewTask> {

    // Loads Task and TaskGroup in a single query
    @Query("SELECT t FROM OrViewTask t JOIN FETCH t.taskGroup WHERE t.id = :id")
    Optional<OrViewTask> findByIdWithTaskGroup(@Param("id") long id);
}
