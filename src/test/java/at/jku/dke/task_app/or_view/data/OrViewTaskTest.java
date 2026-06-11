package at.jku.dke.task_app.or_view.data;

import at.jku.dke.task_app.or_view.data.entities.OrViewTask;
import at.jku.dke.task_app.or_view.data.entities.OrViewTaskGroup;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrViewTaskTest {

    @Test
    void testGetSetTitle() {

        var task = new OrViewTask();
        var expected = "title-test";

        task.setTitle(expected);
        var actual = task.getTitle();

        assertEquals(expected, actual);
    }

    @Test
    void testGetSetDescription() {
        // Arrange
        var task = new OrViewTask();
        var expected = "description-test";

        task.setDescription(expected);
        var actual = task.getDescription();

        assertEquals(expected, actual);
    }

    @Test
    void testGetSetTaskGroup() {

        var task = new OrViewTask();
        var group = new OrViewTaskGroup();

        task.setTaskGroup(group);
        var actual = task.getTaskGroup();

        assertEquals(group, actual);
    }

    @Test
    void testGetSetSolution() {

        var task = new OrViewTask();
        var expected = "SELECT * FROM table";

        task.setSolution(expected);
        var actual = task.getSolution();

        assertEquals(expected, actual);
    }

    @Test
    void testGetSetTestQuery() {

        var task = new OrViewTask();
        var expected = "SELECT COUNT(*) FROM table";

        task.setTestQuery(expected);
        var actual = task.getTestQuery();

        assertEquals(expected, actual);
    }

    @Test
    void testGetSetExpectedIdentifier() {

        var task = new OrViewTask();
        var expected = "obj-123";

        task.setExpectedIdentifier(expected);
        var actual = task.getExpectedIdentifier();

        assertEquals(expected, actual);
    }

    @Test
    void testGetSetUnderSuperview() {
        var task = new OrViewTask();
        var expected = "CREATE OR REPLACE VIEW super_view OF super_ty AS SELECT id FROM t;";
        task.setUnderSuperview(expected);
        assertEquals(expected, task.getUnderSuperview());
    }

    @Test
    void testGetSetRefSuperview() {
        var task = new OrViewTask();
        var expected = "CREATE OR REPLACE VIEW ref_view OF ref_ty AS SELECT id FROM t;";
        task.setRefSuperview(expected);
        assertEquals(expected, task.getRefSuperview());
    }

    @Test
    void testGetSetMissingPrimitiveFieldPenalty() {
        var task = new OrViewTask();
        var expected = new BigDecimal("2.5");
        task.setMissingPrimitiveFieldPenalty(expected);
        assertEquals(expected, task.getMissingPrimitiveFieldPenalty());
    }

    @Test
    void testGetSetMissingObjectFieldPenalty() {
        var task = new OrViewTask();
        var expected = new BigDecimal("3");
        task.setMissingObjectFieldPenalty(expected);
        assertEquals(expected, task.getMissingObjectFieldPenalty());
    }

    @Test
    void testGetSetMissingNestedTablePenalty() {
        var task = new OrViewTask();
        var expected = new BigDecimal("4");
        task.setMissingNestedTablePenalty(expected);
        assertEquals(expected, task.getMissingNestedTablePenalty());
    }

    @Test
    void testGetSetWrongNestedTableTypePenalty() {
        var task = new OrViewTask();
        var expected = new BigDecimal("1");
        task.setWrongNestedTableTypePenalty(expected);
        assertEquals(expected, task.getWrongNestedTableTypePenalty());
    }

    @Test
    void testGetSetWrongViewObjectTypePenalty() {
        var task = new OrViewTask();
        var expected = new BigDecimal("2");
        task.setWrongViewObjectTypePenalty(expected);
        assertEquals(expected, task.getWrongViewObjectTypePenalty());
    }

    @Test
    void testGetSetWrongOidPenalty() {
        var task = new OrViewTask();
        var expected = new BigDecimal("5");
        task.setWrongOidPenalty(expected);
        assertEquals(expected, task.getWrongOidPenalty());
    }

    @Test
    void testGetSetWrongContentPenalty() {
        var task = new OrViewTask();
        var expected = new BigDecimal("6");
        task.setWrongContentPenalty(expected);
        assertEquals(expected, task.getWrongContentPenalty());
    }
}
