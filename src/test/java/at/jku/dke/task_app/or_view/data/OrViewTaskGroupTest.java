package at.jku.dke.task_app.or_view.data;

import at.jku.dke.task_app.or_view.data.entities.OrViewTaskGroup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrViewTaskGroupTest {

    @Test
    void testGetSetTitle() {
        // Arrange
        var taskGroup = new OrViewTaskGroup();
        var value = "title-test";

        // Act
        taskGroup.setTitle(value);
        var result = taskGroup.getTitle();

        // Assert
        assertEquals(value, result);
    }

    @Test
    void testGetSetDescription() {
        // Arrange
        var taskGroup = new OrViewTaskGroup();
        var value = "description-test";

        // Act
        taskGroup.setDescription(value);
        var result = taskGroup.getDescription();

        // Assert
        assertEquals(value, result);
    }

    @Test
    void testGetSetIntensionalSchema() {
        // Arrange
        var taskGroup = new OrViewTaskGroup();
        var value = "intensional-schema";

        // Act
        taskGroup.setIntensionalSchema(value);
        var result = taskGroup.getIntensionalSchema();

        // Assert
        assertEquals(value, result);
    }

    @Test
    void testGetSetExtensionalSchema() {
        // Arrange
        var taskGroup = new OrViewTaskGroup();
        var value = "extensional-schema";

        // Act
        taskGroup.setExtensionalSchema(value);
        var result = taskGroup.getExtensionalSchema();

        // Assert
        assertEquals(value, result);
    }

    @Test
    void testGetSetSubmitInserts() {
        // Arrange
        var taskGroup = new OrViewTaskGroup();
        var value = "submit-inserts";

        // Act
        taskGroup.setSubmitInserts(value);
        var result = taskGroup.getSubmitInserts();

        // Assert
        assertEquals(value, result);
    }

    @Test
    void testGetSetDiagnoseInserts() {
        // Arrange
        var taskGroup = new OrViewTaskGroup();
        var value = "diagnose-inserts";

        // Act
        taskGroup.setDiagnoseInserts(value);
        var result = taskGroup.getDiagnoseInserts();

        // Assert
        assertEquals(value, result);
    }
}
