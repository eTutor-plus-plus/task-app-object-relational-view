package at.jku.dke.task_app.or_view.data.entities;

import at.jku.dke.etutor.task_app.data.entities.BaseTask;
import at.jku.dke.etutor.task_app.data.entities.TaskInGroup;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "task")
public class OrViewTask extends BaseTask implements TaskInGroup<OrViewTaskGroup> {

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_group_id", nullable = false)
    private OrViewTaskGroup taskGroup;

    @Column(name = "solution", nullable = false, length = Integer.MAX_VALUE)
    private String solution;

    @Column(name = "test_query", nullable = false, length = Integer.MAX_VALUE)
    private String testQuery;

    @Column(name = "expected_identifier")
    private String expectedIdentifier;

    @Column(name = "under_superview", length = Integer.MAX_VALUE)
    private String underSuperview;

    @Column(name = "ref_superview", length = Integer.MAX_VALUE)
    private String refSuperview;

    @Column(name = "missing_primitive_field_penalty")
    private BigDecimal missingPrimitiveFieldPenalty;

    @Column(name = "missing_object_field_penalty")
    private BigDecimal missingObjectFieldPenalty;

    @Column(name = "missing_nested_table_penalty")
    private BigDecimal missingNestedTablePenalty;

    @Column(name = "wrong_nested_table_type_penalty")
    private BigDecimal wrongNestedTableTypePenalty;

    @Column(name = "wrong_view_object_type_penalty")
    private BigDecimal wrongViewObjectTypePenalty;

    @Column(name = "wrong_oid_penalty")
    private BigDecimal wrongOidPenalty;

    @Column(name = "wrong_content_penalty")
    private BigDecimal wrongContentPenalty;

    @Column(name = "wrong_column_order_penalty")
    private BigDecimal wrongColumnOrderPenalty;

    @Column(name = "wrong_superview_penalty")
    private BigDecimal wrongSuperviewPenalty;

    public OrViewTask() {}

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public OrViewTaskGroup getTaskGroup() { return taskGroup; }
    public void setTaskGroup(OrViewTaskGroup taskGroup) { this.taskGroup = taskGroup; }

    public String getSolution() { return solution; }
    public void setSolution(String solution) { this.solution = solution; }

    public String getTestQuery() { return testQuery; }
    public void setTestQuery(String testQuery) { this.testQuery = testQuery; }

    public String getExpectedIdentifier() { return expectedIdentifier; }
    public void setExpectedIdentifier(String expectedIdentifier) { this.expectedIdentifier = expectedIdentifier; }

    public String getUnderSuperview() { return underSuperview; }
    public void setUnderSuperview(String underSuperview) { this.underSuperview = underSuperview; }

    public String getRefSuperview() { return refSuperview; }
    public void setRefSuperview(String refSuperview) { this.refSuperview = refSuperview; }

    public BigDecimal getMissingPrimitiveFieldPenalty() { return missingPrimitiveFieldPenalty; }
    public void setMissingPrimitiveFieldPenalty(BigDecimal missingPrimitiveFieldPenalty) { this.missingPrimitiveFieldPenalty = missingPrimitiveFieldPenalty; }

    public BigDecimal getMissingObjectFieldPenalty() { return missingObjectFieldPenalty; }
    public void setMissingObjectFieldPenalty(BigDecimal missingObjectFieldPenalty) { this.missingObjectFieldPenalty = missingObjectFieldPenalty; }

    public BigDecimal getMissingNestedTablePenalty() { return missingNestedTablePenalty; }
    public void setMissingNestedTablePenalty(BigDecimal missingNestedTablePenalty) { this.missingNestedTablePenalty = missingNestedTablePenalty; }

    public BigDecimal getWrongNestedTableTypePenalty() { return wrongNestedTableTypePenalty; }
    public void setWrongNestedTableTypePenalty(BigDecimal wrongNestedTableTypePenalty) { this.wrongNestedTableTypePenalty = wrongNestedTableTypePenalty; }

    public BigDecimal getWrongViewObjectTypePenalty() { return wrongViewObjectTypePenalty; }
    public void setWrongViewObjectTypePenalty(BigDecimal wrongViewObjectTypePenalty) { this.wrongViewObjectTypePenalty = wrongViewObjectTypePenalty; }

    public BigDecimal getWrongOidPenalty() { return wrongOidPenalty;}
    public void setWrongOidPenalty(BigDecimal wrongOidPenalty) { this.wrongOidPenalty = wrongOidPenalty;}

    public BigDecimal getWrongContentPenalty() { return wrongContentPenalty;}
    public void setWrongContentPenalty(BigDecimal wrongContentPenalty) { this.wrongContentPenalty = wrongContentPenalty;}
    public BigDecimal getWrongColumnOrderPenalty() { return wrongColumnOrderPenalty; }
    public void setWrongColumnOrderPenalty(BigDecimal wrongColumnOrderPenalty) { this.wrongColumnOrderPenalty = wrongColumnOrderPenalty; }

    public BigDecimal getWrongSuperviewPenalty() { return wrongSuperviewPenalty; }
    public void setWrongSuperviewPenalty(BigDecimal wrongSuperviewPenalty) { this.wrongSuperviewPenalty = wrongSuperviewPenalty; }
}
