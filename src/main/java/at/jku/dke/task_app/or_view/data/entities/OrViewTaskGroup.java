package at.jku.dke.task_app.or_view.data.entities;

import at.jku.dke.etutor.task_app.data.entities.BaseTaskGroup;
import jakarta.persistence.*;

/**
 * JPA entity representing an OR-View task group with schema definitions and test data.
 */
@Entity
@Table(name = "task_group")
public class OrViewTaskGroup extends BaseTaskGroup {

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "intensional_schema", nullable = false, length = Integer.MAX_VALUE)
    private String intensionalSchema;

    @Column(name = "extensional_schema", nullable = false, length = Integer.MAX_VALUE)
    private String extensionalSchema;

    @Column(name = "submit_inserts", nullable = false, length = Integer.MAX_VALUE)
    private String submitInserts;

    @Column(name = "diagnose_inserts", nullable = false, length = Integer.MAX_VALUE)
    private String diagnoseInserts;

    public OrViewTaskGroup() {}

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIntensionalSchema() {
        return intensionalSchema;
    }

    public void setIntensionalSchema(String intensionalSchema) {
        this.intensionalSchema = intensionalSchema;
    }

    public String getExtensionalSchema() {
        return extensionalSchema;
    }

    public void setExtensionalSchema(String extensionalSchema) {
        this.extensionalSchema = extensionalSchema;
    }

    public String getSubmitInserts() {
        return submitInserts;
    }

    public void setSubmitInserts(String submitInserts) {
        this.submitInserts = submitInserts;
    }

    public String getDiagnoseInserts() {
        return diagnoseInserts;
    }

    public void setDiagnoseInserts(String diagnoseInserts) {
        this.diagnoseInserts = diagnoseInserts;
    }
}
