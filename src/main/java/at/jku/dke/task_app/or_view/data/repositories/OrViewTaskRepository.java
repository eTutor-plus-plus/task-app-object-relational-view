package at.jku.dke.task_app.or_view.data.repositories;

import at.jku.dke.etutor.task_app.data.repositories.TaskRepository;
import at.jku.dke.task_app.or_view.data.entities.OrViewTask;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrViewTaskRepository extends TaskRepository<OrViewTask> {

    // Lädt Task und TaskGroup in einer Query
    @Query("SELECT t FROM OrViewTask t JOIN FETCH t.taskGroup WHERE t.id = :id")
    Optional<OrViewTask> findByIdWithTaskGroup(@Param("id") long id);
}
