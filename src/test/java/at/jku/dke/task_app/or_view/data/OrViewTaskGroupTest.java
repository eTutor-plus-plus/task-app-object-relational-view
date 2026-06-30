package at.jku.dke.task_app.or_view.data;

import at.jku.dke.task_app.or_view.data.entities.OrViewTaskGroup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrViewTaskGroupTest {

    @Test
    void testGetSetTitle() {

        var taskGroup = new OrViewTaskGroup();
        var value = "title-test";

        taskGroup.setTitle(value);
        var result = taskGroup.getTitle();
        assertEquals(value, result);
    }

    @Test
    void testGetSetDescription() {
        var taskGroup = new OrViewTaskGroup();
        var value = "description-test";

        taskGroup.setDescription(value);
        var result = taskGroup.getDescription();

        assertEquals(value, result);
    }

    @Test
    void testGetSetIntensionalSchema() {

        var taskGroup = new OrViewTaskGroup();
        var value = "intensional-schema";

        taskGroup.setIntensionalSchema(value);
        var result = taskGroup.getIntensionalSchema();

        assertEquals(value, result);
    }

    @Test
    void testGetSetExtensionalSchema() {
        var taskGroup = new OrViewTaskGroup();
        var value = "extensional-schema";

        taskGroup.setExtensionalSchema(value);
        var result = taskGroup.getExtensionalSchema();

        assertEquals(value, result);
    }

    @Test
    void testGetSetSubmitInserts() {
        var taskGroup = new OrViewTaskGroup();
        var value = "submit-inserts";

        taskGroup.setSubmitInserts(value);
        var result = taskGroup.getSubmitInserts();

        assertEquals(value, result);
    }

    @Test
    void testGetSetDiagnoseInserts() {

        var taskGroup = new OrViewTaskGroup();
        var value = "diagnose-inserts";

        taskGroup.setDiagnoseInserts(value);
        var result = taskGroup.getDiagnoseInserts();

        assertEquals(value, result);
    }
}
