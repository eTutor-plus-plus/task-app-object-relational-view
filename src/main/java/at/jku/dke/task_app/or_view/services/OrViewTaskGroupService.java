package at.jku.dke.task_app.or_view.services;

import at.jku.dke.etutor.task_app.dto.ModifyTaskGroupDto;
import at.jku.dke.etutor.task_app.dto.TaskGroupModificationResponseDto;
import at.jku.dke.etutor.task_app.dto.TaskStatus;
import at.jku.dke.etutor.task_app.services.BaseTaskGroupService;
import at.jku.dke.task_app.or_view.data.entities.OrViewTaskGroup;
import at.jku.dke.task_app.or_view.data.repositories.OrViewTaskGroupRepository;
import at.jku.dke.task_app.or_view.dto.ModifyOrViewTaskGroupDto;
import at.jku.dke.task_app.or_view.evaluation.OrViewDataSource;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Service for managing OR-View task groups including schema setup and validation.
 */
@Service
public class OrViewTaskGroupService extends BaseTaskGroupService<OrViewTaskGroup, ModifyOrViewTaskGroupDto> {

    private final OrViewDataSource dataSource;
    private final MessageSource messageSource;

    public OrViewTaskGroupService(OrViewTaskGroupRepository repository,
                                  OrViewDataSource dataSource,
                                  MessageSource messageSource) {
        super(repository);
        this.dataSource = dataSource;
        this.messageSource = messageSource;
    }

    @Override
    protected OrViewTaskGroup createTaskGroup(long id, ModifyTaskGroupDto<ModifyOrViewTaskGroupDto> dto) {
        var tg = new OrViewTaskGroup();
        tg.setId(id);
        tg.setStatus(TaskStatus.DRAFT);
        tg.setTitle("ORV Group " + id);
        tg.setDescription("Object Relational View Task Group");
        tg.setIntensionalSchema(dto.additionalData().intensionalSchema());
        tg.setExtensionalSchema(dto.additionalData().extensionalSchema());
        tg.setSubmitInserts(dto.additionalData().submitInserts());
        tg.setDiagnoseInserts(dto.additionalData().diagnoseInserts());
        return tg;
    }

    @Override
    protected void updateTaskGroup(OrViewTaskGroup tg, ModifyTaskGroupDto<ModifyOrViewTaskGroupDto> dto) {
        tg.setIntensionalSchema(dto.additionalData().intensionalSchema());
        tg.setExtensionalSchema(dto.additionalData().extensionalSchema());
        tg.setSubmitInserts(dto.additionalData().submitInserts());
        tg.setDiagnoseInserts(dto.additionalData().diagnoseInserts());
    }

    @Override
    protected TaskGroupModificationResponseDto mapToReturnData(OrViewTaskGroup taskGroup, boolean create) {
        return new TaskGroupModificationResponseDto("OR-View TaskGroup", "OR-View TaskGroup");
    }

    @Override
    protected void afterCreate(OrViewTaskGroup tg, ModifyTaskGroupDto<ModifyOrViewTaskGroupDto> dto) {
        setupAndValidateSchema(tg);
    }

    @Override
    protected void afterUpdate(OrViewTaskGroup tg, ModifyTaskGroupDto<ModifyOrViewTaskGroupDto> dto) {
        setupAndValidateSchema(tg);
    }

    private void setupAndValidateSchema(OrViewTaskGroup tg) {
        String schema = "ORV_" + tg.getId();

        try (var service = new OrViewSchemaServiceImpl(this.dataSource)) {
            service.initForSchema(schema);
            service.dropSchema(schema);
            service.createSchema(schema);

            service.executeStatements(schema, tg.getIntensionalSchema());
            service.executeStatements(schema, tg.getExtensionalSchema());
            service.executeStatements(schema, tg.getDiagnoseInserts());

            service.commit();

        } catch (Exception ex) {
            LOG.error("Schema creation failed for task group {}", tg.getId(), ex);
            Locale locale = LocaleContextHolder.getLocale();
            throw new RuntimeException(
                messageSource.getMessage("error.schemaCreationFailed",
                    new Object[]{ex.getMessage()}, locale), ex);
        }

        validateSchemaTypes(schema);
    }

    private void validateSchemaTypes(String schema) {
        try (var conn = dataSource.connectExecutor(); var stmt = conn.createStatement()) {

            stmt.execute("ALTER SESSION SET CURRENT_SCHEMA = " + schema.toUpperCase());

            var rs = stmt.executeQuery(
                "SELECT t.object_name FROM all_objects t WHERE t.owner = '"
                    + schema.toUpperCase()
                    + "' AND t.object_type = 'TYPE' "
                    + "AND NOT EXISTS (SELECT 1 FROM all_objects b WHERE b.owner = t.owner "
                    + "AND b.object_type = 'TYPE BODY' AND b.object_name = t.object_name) "
                    + "AND EXISTS (SELECT 1 FROM all_source s WHERE s.owner = t.owner "
                    + "AND s.type = 'TYPE' AND s.name = t.object_name "
                    + "AND UPPER(s.text) LIKE '%MEMBER%')");

            List<String> missingBodies = new ArrayList<>();
            while (rs.next()) {
                missingBodies.add(rs.getString(1));
            }

            if (!missingBodies.isEmpty()) {
                throw new RuntimeException(
                    "Missing TYPE BODY for types with MEMBER functions: "
                        + String.join(", ", missingBodies)
                        + ". Check for missing '/' separators in the intensional schema.");
            }

            var rs2 = stmt.executeQuery(
                "SELECT object_name FROM all_objects WHERE owner = '"
                    + schema.toUpperCase()
                    + "' AND object_type = 'TYPE'");

            List<String> typeNames = new ArrayList<>();
            while (rs2.next()) {
                typeNames.add(rs2.getString(1));
            }

            for (String typeName : typeNames) {
                try (var stmt2 = conn.createStatement()) {
                    stmt2.executeQuery(
                        "SELECT attr_name FROM all_type_attrs WHERE owner = '"
                            + schema.toUpperCase() + "' AND type_name = '" + typeName + "'");
                } catch (Exception ignored) {}
            }

            var rs3 = stmt.executeQuery(
                "SELECT object_name, object_type FROM all_objects WHERE owner = '"
                    + schema.toUpperCase()
                    + "' AND object_type = 'TYPE' AND status = 'INVALID'");

            List<String> invalidTypes = new ArrayList<>();
            while (rs3.next()) {
                invalidTypes.add(rs3.getString("OBJECT_NAME"));
            }

            if (!invalidTypes.isEmpty()) {
                throw new RuntimeException(
                    "Invalid types in schema: " + String.join(", ", invalidTypes)
                        + ". Check type order and dependencies in the intensional schema.");
            }

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            LOG.warn("Schema type validation failed for {}: {}", schema, e.getMessage());
        }
    }
}
