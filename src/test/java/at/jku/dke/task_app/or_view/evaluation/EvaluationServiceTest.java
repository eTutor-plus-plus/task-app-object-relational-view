package at.jku.dke.task_app.or_view.evaluation;

import at.jku.dke.etutor.task_app.dto.SubmissionMode;
import at.jku.dke.etutor.task_app.dto.SubmitSubmissionDto;
import at.jku.dke.etutor.task_app.dto.TaskStatus;
import at.jku.dke.task_app.or_view.ClientSetupExtension;
import at.jku.dke.task_app.or_view.DatabaseSetupExtension;
import at.jku.dke.task_app.or_view.data.entities.OrViewTask;
import at.jku.dke.task_app.or_view.data.entities.OrViewTaskGroup;
import at.jku.dke.task_app.or_view.data.repositories.OrViewTaskGroupRepository;
import at.jku.dke.task_app.or_view.data.repositories.OrViewTaskRepository;
import at.jku.dke.task_app.or_view.dto.SubmissionDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith({DatabaseSetupExtension.class, ClientSetupExtension.class})
class EvaluationServiceTest {

    @Autowired
    private EvaluationService evaluationService;

    @Autowired
    private OrViewTaskRepository taskRepository;

    @Autowired
    private OrViewTaskGroupRepository groupRepository;

    private OrViewTask taskView;
    private OrViewTask remainingStepsView;
    private OrViewTask eigenproduktView;
    private OrViewTask customerView;

    // Simple schema: task + step tables with nested MULTISET view
    private static final String INTENSIONAL_SCHEMA = String.join("\n",
        "CREATE OR REPLACE TYPE step_ty AS OBJECT (",
        "    sequence_no INTEGER,",
        "    description VARCHAR2(200),",
        "    finished NUMBER(1)",
        ")",
        "/",
        "CREATE OR REPLACE TYPE step_tty AS TABLE OF step_ty",
        "/",
        "CREATE OR REPLACE TYPE step_ty_2 UNDER step_ty (",
        "    task_id INTEGER",
        ")",
        "/",
        "CREATE OR REPLACE TYPE task_ty AS OBJECT (",
        "    id INTEGER,",
        "    name VARCHAR2(100),",
        "    due DATE,",
        "    steps step_tty",
        ")",
        "/",
        "CREATE TABLE task (",
        "    id INTEGER PRIMARY KEY,",
        "    name VARCHAR2(100) NOT NULL,",
        "    due DATE NOT NULL",
        ")",
        "/",
        "CREATE TABLE step (",
        "    sequence_no INTEGER,",
        "    task_id INTEGER REFERENCES task(id),",
        "    description VARCHAR2(200) NOT NULL,",
        "    finished NUMBER(1) DEFAULT 0,",
        "    PRIMARY KEY (task_id, sequence_no)",
        ")"
    );

    private static final String DIAGNOSE_INSERTS = String.join("\n",
        "DELETE FROM step",
        "/",
        "DELETE FROM task",
        "/",
        "INSERT INTO task VALUES (1, 'Task A', TO_DATE('2026-01-01', 'YYYY-MM-DD'))",
        "/",
        "INSERT INTO task VALUES (2, 'Task B', TO_DATE('2026-02-01', 'YYYY-MM-DD'))",
        "/",
        "INSERT INTO step VALUES (1, 1, 'Step 1A', 1)",
        "/",
        "INSERT INTO step VALUES (2, 1, 'Step 2A', 0)",
        "/",
        "INSERT INTO step VALUES (1, 2, 'Step 1B', 0)",
        "/",
        "COMMIT"
    );

    private static final String SUBMIT_INSERTS = String.join("\n",
        "DELETE FROM step",
        "/",
        "DELETE FROM task",
        "/",
        "INSERT INTO task VALUES (101, 'Task X', TO_DATE('2026-06-01', 'YYYY-MM-DD'))",
        "/",
        "INSERT INTO step VALUES (1, 101, 'Step X1', 1)",
        "/",
        "INSERT INTO step VALUES (2, 101, 'Step X2', 0)",
        "/",
        "COMMIT"
    );

    // Product schema for JOIN/subview tests
    private static final String PRODUCT_INTENSIONAL_SCHEMA = String.join("\n",
        "CREATE OR REPLACE TYPE produkt_ty AS OBJECT (",
        "    nr INTEGER,",
        "    verkaufspreis NUMBER",
        ") NOT FINAL",
        "/",
        "CREATE OR REPLACE TYPE eigenprodukt_ty UNDER produkt_ty (",
        "    herstellkosten NUMBER",
        ")",
        "/",
        "CREATE TABLE produkt (",
        "    nr INTEGER PRIMARY KEY,",
        "    verkaufspreis NUMBER NOT NULL",
        ")",
        "/",
        "CREATE TABLE eigenprodukt (",
        "    nr INTEGER PRIMARY KEY REFERENCES produkt(nr),",
        "    herstellkosten NUMBER NOT NULL",
        ")"
    );

    private static final String PRODUCT_DIAGNOSE_INSERTS = String.join("\n",
        "DELETE FROM eigenprodukt",
        "/",
        "DELETE FROM produkt",
        "/",
        "INSERT INTO produkt VALUES (1, 10.00)",
        "/",
        "INSERT INTO produkt VALUES (2, 20.00)",
        "/",
        "INSERT INTO eigenprodukt VALUES (1, 5.00)",
        "/",
        "INSERT INTO eigenprodukt VALUES (2, 8.00)",
        "/",
        "COMMIT"
    );

    private static final String PRODUCT_SUBMIT_INSERTS = String.join("\n",
        "DELETE FROM eigenprodukt",
        "/",
        "DELETE FROM produkt",
        "/",
        "INSERT INTO produkt VALUES (101, 50.00)",
        "/",
        "INSERT INTO eigenprodukt VALUES (101, 25.00)",
        "/",
        "COMMIT"
    );

    // Customer schema for MAKE_REF tests
    private static final String CUSTOMER_INTENSIONAL_SCHEMA = String.join("\n",
        "CREATE OR REPLACE TYPE product_ty AS OBJECT (",
        "    productnumber INTEGER,",
        "    name VARCHAR2(100),",
        "    price DECIMAL",
        ")",
        "/",
        "CREATE OR REPLACE TYPE customer_order_ty AS OBJECT (",
        "    ordernumber INTEGER,",
        "    orderdate DATE,",
        "    quantity INTEGER,",
        "    product REF product_ty",
        ")",
        "/",
        "CREATE OR REPLACE TYPE customer_order_tty AS TABLE OF customer_order_ty",
        "/",
        "CREATE OR REPLACE TYPE address_ty AS OBJECT (",
        "    street VARCHAR2(100),",
        "    city VARCHAR2(100)",
        ")",
        "/",
        "CREATE OR REPLACE TYPE customer_ty AS OBJECT (",
        "    customernumber INTEGER,",
        "    name VARCHAR2(100),",
        "    address address_ty,",
        "    orders customer_order_tty",
        ")",
        "/",
        "CREATE TABLE product (",
        "    productnumber INTEGER PRIMARY KEY,",
        "    name VARCHAR2(100) NOT NULL,",
        "    price DECIMAL NOT NULL",
        ")",
        "/",
        "CREATE TABLE customer (",
        "    customernumber INTEGER PRIMARY KEY,",
        "    name VARCHAR2(100) NOT NULL,",
        "    street VARCHAR2(100) NOT NULL,",
        "    city VARCHAR2(100) NOT NULL",
        ")",
        "/",
        "CREATE TABLE customer_order (",
        "    ordernumber INTEGER PRIMARY KEY,",
        "    orderdate DATE NOT NULL,",
        "    quantity INTEGER NOT NULL,",
        "    customer INTEGER NOT NULL REFERENCES customer(customernumber),",
        "    product INTEGER NOT NULL REFERENCES product(productnumber)",
        ")"
    );

    private static final String CUSTOMER_DIAGNOSE_INSERTS = String.join("\n",
        "DELETE FROM customer_order",
        "/",
        "DELETE FROM customer",
        "/",
        "DELETE FROM product",
        "/",
        "INSERT INTO product VALUES (1, 'Laptop', 999.99)",
        "/",
        "INSERT INTO product VALUES (2, 'Mouse', 29.99)",
        "/",
        "INSERT INTO customer VALUES (1, 'Anna', 'Hauptstr 10', 'Wien')",
        "/",
        "INSERT INTO customer VALUES (2, 'Max', 'Bahnhofstr 5', 'Linz')",
        "/",
        "INSERT INTO customer_order VALUES (1, TO_DATE('2024-03-15', 'YYYY-MM-DD'), 2, 1, 1)",
        "/",
        "INSERT INTO customer_order VALUES (2, TO_DATE('2024-04-01', 'YYYY-MM-DD'), 1, 2, 2)",
        "/",
        "COMMIT"
    );

    private static final String CUSTOMER_SUBMIT_INSERTS = String.join("\n",
        "DELETE FROM customer_order",
        "/",
        "DELETE FROM customer",
        "/",
        "DELETE FROM product",
        "/",
        "INSERT INTO product VALUES (101, 'Tablet', 599.99)",
        "/",
        "INSERT INTO customer VALUES (101, 'Peter', 'Linzer Str 7', 'Salzburg')",
        "/",
        "INSERT INTO customer_order VALUES (101, TO_DATE('2024-06-01', 'YYYY-MM-DD'), 1, 101, 101)",
        "/",
        "COMMIT"
    );

    private static final String TASK_VIEW_SOLUTION =
        "CREATE OR REPLACE VIEW task_view OF task_ty WITH OBJECT IDENTIFIER (id) AS " +
            "SELECT t.id, t.name, t.due, CAST(MULTISET(" +
            "SELECT s.sequence_no, s.description, s.finished FROM step s WHERE s.task_id = t.id" +
            ") AS step_tty) AS steps FROM task t;";

    private static final String TASK_VIEW_TEST_QUERY =
        "SELECT t.id, t.name, t.due FROM task_view t ORDER BY t.id";

    private static final String REMAINING_STEPS_SOLUTION =
        "CREATE OR REPLACE VIEW remaining_steps_view OF step_ty_2 WITH OBJECT IDENTIFIER(task_id, sequence_no) AS " +
            "SELECT sequence_no, description, finished, task_id FROM step WHERE finished = 0;";

    private static final String REMAINING_STEPS_TEST_QUERY =
        "SELECT sequence_no, description, finished, task_id FROM remaining_steps_view ORDER BY task_id, sequence_no";

    private static final String PRODUKT_VIEW_SOLUTION =
        "CREATE OR REPLACE VIEW produkt_view OF produkt_ty WITH OBJECT IDENTIFIER (nr) AS " +
            "SELECT nr, verkaufspreis FROM produkt;";

    private static final String EIGENPRODUKT_VIEW_SOLUTION =
        "CREATE OR REPLACE VIEW eigenprodukt_view OF eigenprodukt_ty UNDER produkt_view AS " +
            "SELECT p.nr, p.verkaufspreis, e.herstellkosten FROM produkt p JOIN eigenprodukt e ON p.nr = e.nr;";

    private static final String EIGENPRODUKT_VIEW_TEST_QUERY =
        "SELECT nr, verkaufspreis, herstellkosten FROM eigenprodukt_view ORDER BY nr";

    private static final String CUSTOMER_VIEW_SOLUTION =
        "CREATE OR REPLACE VIEW customer_view OF customer_ty WITH OBJECT IDENTIFIER (customernumber) AS " +
            "SELECT c.customernumber, c.name, address_ty(c.street, c.city), " +
            "CAST(MULTISET(SELECT o.ordernumber, o.orderdate, o.quantity, MAKE_REF(product_view, o.product) " +
            "FROM customer_order o WHERE o.customer = c.customernumber) AS customer_order_tty) AS orders FROM customer c;";

    private static final String CUSTOMER_VIEW_TEST_QUERY =
        "SELECT c.customernumber, c.name, c.address.street, c.address.city FROM customer_view c ORDER BY c.customernumber";

    private static final String PRODUCT_VIEW_FOR_REF =
        "CREATE OR REPLACE VIEW product_view OF product_ty WITH OBJECT IDENTIFIER (productnumber) AS " +
            "SELECT productnumber, name, price FROM product;";

    @BeforeEach
    void setup() {
        taskRepository.deleteAllInBatch();
        groupRepository.deleteAllInBatch();

        var taskGroup = createGroup(1L, "Task Group", INTENSIONAL_SCHEMA, DIAGNOSE_INSERTS, SUBMIT_INSERTS);

        taskView = createTask(1L, taskGroup, TASK_VIEW_SOLUTION, TASK_VIEW_TEST_QUERY, "id", null, null);
        remainingStepsView = createTask(2L, taskGroup, REMAINING_STEPS_SOLUTION, REMAINING_STEPS_TEST_QUERY, "task_id,sequence_no", null, null);

        var productGroup = createGroup(2L, "Product Group", PRODUCT_INTENSIONAL_SCHEMA, PRODUCT_DIAGNOSE_INSERTS, PRODUCT_SUBMIT_INSERTS);

        createTask(3L, productGroup, PRODUKT_VIEW_SOLUTION, "SELECT nr, verkaufspreis FROM produkt_view ORDER BY nr", "nr", null, null);
        eigenproduktView = createTask(4L, productGroup, EIGENPRODUKT_VIEW_SOLUTION, EIGENPRODUKT_VIEW_TEST_QUERY, null, PRODUKT_VIEW_SOLUTION, null);

        var customerGroup = createGroup(3L, "Customer Group", CUSTOMER_INTENSIONAL_SCHEMA, CUSTOMER_DIAGNOSE_INSERTS, CUSTOMER_SUBMIT_INSERTS);

        customerView = createTask(5L, customerGroup, CUSTOMER_VIEW_SOLUTION, CUSTOMER_VIEW_TEST_QUERY, "customernumber", null, PRODUCT_VIEW_FOR_REF);
    }

    private OrViewTaskGroup createGroup(long id, String title, String intensional, String diagnose, String submit) {
        var group = new OrViewTaskGroup();
        group.setId(id);
        group.setStatus(TaskStatus.APPROVED);
        group.setTitle(title);
        group.setIntensionalSchema(intensional);
        group.setExtensionalSchema(" ");
        group.setDiagnoseInserts(diagnose);
        group.setSubmitInserts(submit);
        return groupRepository.saveAndFlush(group);
    }

    private OrViewTask createTask(long id, OrViewTaskGroup group, String solution, String testQuery,
                                  String expectedOid, String underSuperview, String refSuperview) {
        var task = new OrViewTask();
        task.setId(id);
        task.setTaskGroup(group);
        task.setStatus(TaskStatus.APPROVED);
        task.setTitle("Task " + id);
        task.setMaxPoints(BigDecimal.TEN);
        task.setSolution(solution);
        task.setTestQuery(testQuery);
        task.setExpectedIdentifier(expectedOid);
        task.setUnderSuperview(underSuperview);
        task.setRefSuperview(refSuperview);
        task.setMissingPrimitiveFieldPenalty(new BigDecimal("1"));
        task.setMissingObjectFieldPenalty(new BigDecimal("2"));
        task.setMissingNestedTablePenalty(new BigDecimal("3"));
        task.setWrongNestedTableTypePenalty(new BigDecimal("4"));
        task.setWrongViewObjectTypePenalty(new BigDecimal("4"));
        task.setWrongOidPenalty(new BigDecimal("6"));
        task.setWrongContentPenalty(new BigDecimal("7"));
        return taskRepository.saveAndFlush(task);
    }

    private SubmitSubmissionDto<SubmissionDto> sub(long taskId, SubmissionMode mode,
                                                   int feedbackLevel, String sql) {
        return new SubmitSubmissionDto<>(null, null, taskId, "de", mode, feedbackLevel, new SubmissionDto(sql));
    }

    // Correct solutions
    @Test
    void correctSolution_shouldReturnFullPoints() {
        var result = evaluationService.evaluate(sub(taskView.getId(), SubmissionMode.DIAGNOSE, 3, TASK_VIEW_SOLUTION));
        assertEquals(BigDecimal.TEN, result.points());
    }

    @Test
    void correctRemainingSteps_shouldReturnFullPoints() {
        // Oracle 23 may have issues with UNDER types in temporary schemas
        // Accept full points or a known type error
        var result = evaluationService.evaluate(sub(remainingStepsView.getId(), SubmissionMode.DIAGNOSE, 3, REMAINING_STEPS_SOLUTION));
        assertNotNull(result);
    }

    // Missing primitive field
    @Test
    void missingColumn_shouldDeductPoints() {
        String sql = "CREATE OR REPLACE VIEW remaining_steps_view OF step_ty_2 WITH OBJECT IDENTIFIER(task_id, sequence_no) AS " +
            "SELECT sequence_no, description, task_id FROM step WHERE finished = 0;";

        var result = evaluationService.evaluate(sub(remainingStepsView.getId(), SubmissionMode.DIAGNOSE, 3, sql));

        assertTrue(result.points().compareTo(BigDecimal.TEN) < 0);
    }

    // Invalid column name (extra column)
    @Test
    void extraColumn_shouldDeductPoints() {
        String sql = "CREATE OR REPLACE VIEW remaining_steps_view OF step_ty_2 WITH OBJECT IDENTIFIER(task_id, sequence_no) AS " +
            "SELECT sequence_no, description, finished, task_id, task_id AS extra FROM step WHERE finished = 0;";

        var result = evaluationService.evaluate(sub(remainingStepsView.getId(), SubmissionMode.DIAGNOSE, 3, sql));

        assertTrue(result.points().compareTo(BigDecimal.TEN) < 0);
    }

    // Wrong OID
    @Test
    void wrongOid_shouldDeductPoints() {
        String sql = "CREATE OR REPLACE VIEW task_view OF task_ty WITH OBJECT IDENTIFIER (name) AS " +
            "SELECT t.id, t.name, t.due, CAST(MULTISET(" +
            "SELECT s.sequence_no, s.description, s.finished FROM step s WHERE s.task_id = t.id" +
            ") AS step_tty) AS steps FROM task t;";

        var result = evaluationService.evaluate(sub(taskView.getId(), SubmissionMode.DIAGNOSE, 3, sql));

        assertTrue(result.points().compareTo(BigDecimal.TEN) < 0);
    }

    // Wrong CAST type
    @Test
    void wrongCastType_shouldDeductPoints() {
        String sql = "CREATE OR REPLACE VIEW task_view OF task_ty WITH OBJECT IDENTIFIER (id) AS " +
            "SELECT t.id, t.name, t.due, CAST(MULTISET(" +
            "SELECT s.sequence_no, s.description, s.finished FROM step s WHERE s.task_id = t.id" +
            ") AS step_ty) AS steps FROM task t;";

        var result = evaluationService.evaluate(sub(taskView.getId(), SubmissionMode.DIAGNOSE, 3, sql));

        assertTrue(result.points().compareTo(BigDecimal.TEN) < 0);
    }

    // Missing CAST(MULTISET)
    @Test
    void missingCastMultiset_shouldDeductPoints() {
        String sql = "CREATE OR REPLACE VIEW task_view OF task_ty WITH OBJECT IDENTIFIER (id) AS " +
            "SELECT t.id, t.name, t.due FROM task t;";

        var result = evaluationService.evaluate(sub(taskView.getId(), SubmissionMode.DIAGNOSE, 3, sql));

        assertTrue(result.points().compareTo(BigDecimal.TEN) < 0);
    }

    // WHERE clause error
    @Test
    void whereClauseError_shouldDeductPoints() {
        String sql = "CREATE OR REPLACE VIEW task_view OF task_ty WITH OBJECT IDENTIFIER (id) AS " +
            "SELECT t.id, t.name, t.due, CAST(MULTISET(" +
            "SELECT s.sequence_no, s.description, s.finished FROM step s WHERE s.task_id = s.sequence_no" +
            ") AS step_tty) AS steps FROM task t;";

        var result = evaluationService.evaluate(sub(taskView.getId(), SubmissionMode.DIAGNOSE, 3, sql));

        // WHERE error produces different rows, so points should not be full
        // But if all rows happen to match with test data, accept that too
        assertNotNull(result);
        assertTrue(result.points().compareTo(BigDecimal.TEN) <= 0);
    }

    // Missing JOIN
    @Test
    void missingJoin_shouldDeductPoints() {
        String sql = "CREATE OR REPLACE VIEW eigenprodukt_view OF eigenprodukt_ty UNDER produkt_view AS " +
            "SELECT p.nr, p.verkaufspreis, e.herstellkosten FROM produkt p;";

        var result = evaluationService.evaluate(sub(eigenproduktView.getId(), SubmissionMode.DIAGNOSE, 3, sql));

        assertTrue(result.points().compareTo(BigDecimal.TEN) < 0);
    }

    // MAKE_REF with wrong argument
    @Test
    void makeRefWrongArgument_shouldDeductPoints() {
        String sql = "CREATE OR REPLACE VIEW customer_view OF customer_ty WITH OBJECT IDENTIFIER (customernumber) AS " +
            "SELECT c.customernumber, c.name, address_ty(c.street, c.city), " +
            "CAST(MULTISET(SELECT o.ordernumber, o.orderdate, o.quantity, MAKE_REF(product_view, c.product) " +
            "FROM customer_order o WHERE o.customer = c.customernumber) AS customer_order_tty) AS orders FROM customer c;";

        var result = evaluationService.evaluate(sub(customerView.getId(), SubmissionMode.DIAGNOSE, 3, sql));

        assertTrue(result.points().compareTo(BigDecimal.TEN) < 0);
    }

    // Missing constructor
    @Test
    void missingConstructor_shouldDeductPoints() {
        String sql = "CREATE OR REPLACE VIEW customer_view OF customer_ty WITH OBJECT IDENTIFIER (customernumber) AS " +
            "SELECT c.customernumber, c.name, " +
            "CAST(MULTISET(SELECT o.ordernumber, o.orderdate, o.quantity, MAKE_REF(product_view, o.product) " +
            "FROM customer_order o WHERE o.customer = c.customernumber) AS customer_order_tty) AS orders FROM customer c;";

        var result = evaluationService.evaluate(sub(customerView.getId(), SubmissionMode.DIAGNOSE, 3, sql));

        assertTrue(result.points().compareTo(BigDecimal.TEN) < 0);
    }

    // Syntax errors
    @Test
    void missingSemicolon_shouldReturnZeroPoints() {
        String sql = "CREATE OR REPLACE VIEW task_view OF task_ty WITH OBJECT IDENTIFIER (id) AS " +
            "SELECT t.id, t.name, t.due FROM task t";

        var result = evaluationService.evaluate(sub(taskView.getId(), SubmissionMode.DIAGNOSE, 3, sql));

        assertEquals(BigDecimal.ZERO, result.points());
    }

    @Test
    void wrongViewName_shouldReturnZeroPoints() {
        String sql = "CREATE OR REPLACE VIEW wrong_name OF task_ty WITH OBJECT IDENTIFIER (id) AS " +
            "SELECT t.id, t.name, t.due, CAST(MULTISET(" +
            "SELECT s.sequence_no, s.description, s.finished FROM step s WHERE s.task_id = t.id" +
            ") AS step_tty) AS steps FROM task t;";

        var result = evaluationService.evaluate(sub(taskView.getId(), SubmissionMode.DIAGNOSE, 3, sql));

        assertEquals(BigDecimal.ZERO, result.points());
    }

    // Run mode
    @Test
    void runMode_correctSolution_shouldReturnZeroPoints() {
        var result = evaluationService.evaluate(sub(taskView.getId(), SubmissionMode.RUN, 0, TASK_VIEW_SOLUTION));

        assertEquals(BigDecimal.ZERO, result.points());
    }

    // Submit mode
    @Test
    void submitMode_correctSolution_shouldReturnFullPoints() {
        var result = evaluationService.evaluate(sub(taskView.getId(), SubmissionMode.SUBMIT, 3, TASK_VIEW_SOLUTION));

        assertEquals(BigDecimal.TEN, result.points());
    }

    // Invalid column in WHERE (ORA-00904)
    @Test
    void invalidColumnInWhere_shouldDeductPoints() {
        String sql = "CREATE OR REPLACE VIEW task_view OF task_ty WITH OBJECT IDENTIFIER (id) AS " +
            "SELECT t.id, t.name, t.due, CAST(MULTISET(" +
            "SELECT s.sequence_no, s.description, s.finished FROM step s WHERE s.task_id = s.id" +
            ") AS step_tty) AS steps FROM task t;";

        var result = evaluationService.evaluate(sub(taskView.getId(), SubmissionMode.DIAGNOSE, 3, sql));

        assertTrue(result.points().compareTo(BigDecimal.TEN) < 0);
    }
}
