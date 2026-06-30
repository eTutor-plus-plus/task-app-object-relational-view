package at.jku.dke.task_app.or_view.services;

import at.jku.dke.etutor.task_app.dto.ModifyTaskDto;
import at.jku.dke.etutor.task_app.dto.TaskModificationResponseDto;
import at.jku.dke.etutor.task_app.dto.TaskStatus;
import at.jku.dke.etutor.task_app.services.BaseTaskInGroupService;
import at.jku.dke.task_app.or_view.data.entities.OrViewTask;
import at.jku.dke.task_app.or_view.data.entities.OrViewTaskGroup;
import at.jku.dke.task_app.or_view.data.repositories.OrViewTaskGroupRepository;
import at.jku.dke.task_app.or_view.data.repositories.OrViewTaskRepository;
import at.jku.dke.task_app.or_view.dto.ModifyOrViewTaskDto;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for managing OR-View tasks including solution validation and penalty configuration.
 */
@Service
public class OrViewTaskService extends BaseTaskInGroupService<OrViewTask, OrViewTaskGroup, ModifyOrViewTaskDto> {

    private final OrViewTaskGroupRepository taskGroupRepository;
    private final MessageSource messageSource;

    private static final Pattern UNDER_PATTERN =
        Pattern.compile("\\bUNDER\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern MAKE_REF_PATTERN =
        Pattern.compile("\\bMAKE_REF\\s*\\(", Pattern.CASE_INSENSITIVE);

    private static final Pattern OID_PATTERN =
        Pattern.compile("WITH\\s+OBJECT\\s+IDENTIFIER\\s*\\(([^)]+)\\)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern VIEW_NAME_PATTERN =
        Pattern.compile("CREATE\\s+(?:OR\\s+REPLACE\\s+)?VIEW\\s+(\\w+)",
            Pattern.CASE_INSENSITIVE);

    /**
     * Creates a new OrViewTaskService.
     *
     * @param repository          the task repository
     * @param taskGroupRepository the task group repository
     * @param messageSource       the message source for localized messages
     */
    public OrViewTaskService(OrViewTaskRepository repository,
                             OrViewTaskGroupRepository taskGroupRepository,
                             MessageSource messageSource) {
        super(repository, taskGroupRepository);
        this.taskGroupRepository = taskGroupRepository;
        this.messageSource = messageSource;
    }

    @Override
    protected OrViewTask createTask(long id, ModifyTaskDto<ModifyOrViewTaskDto> dto) {
        OrViewTask task = new OrViewTask();
        task.setId(id);
        task.setStatus(TaskStatus.DRAFT);
        task.setTitle("ORV Task " + id);
        task.setDescription("Object Relational View Task");

        OrViewTaskGroup group = taskGroupRepository.findById(dto.taskGroupId())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                msg("system.error")
            ));

        task.setTaskGroup(group);
        applyTaskData(task, dto.additionalData());
        return task;
    }

    @Override
    protected void updateTask(OrViewTask task, ModifyTaskDto<ModifyOrViewTaskDto> dto) {
        applyTaskData(task, dto.additionalData());
    }

    private void applyTaskData(OrViewTask task, ModifyOrViewTaskDto data) {

        String solution       = data.solution();
        String underSuperview = data.underSuperview();
        String refSuperview   = data.refSuperview();
        String testQuery      = data.testQuery();

        validateSolutionSemicolon(solution);

        boolean hasUnderSuperviewConfig = underSuperview != null && !underSuperview.isBlank();
        boolean hasRefSuperviewConfig   = refSuperview != null && !refSuperview.isBlank();
        boolean solutionUsesUnder       = UNDER_PATTERN.matcher(solution).find();
        boolean solutionUsesMakeRef     = MAKE_REF_PATTERN.matcher(solution).find();

        // UNDER used in solution but no superview configured
        if (solutionUsesUnder && !hasUnderSuperviewConfig) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg("error.invalidSuperview"));
        }

        // MAKE_REF used in solution but no ref superview configured
        if (solutionUsesMakeRef && !hasRefSuperviewConfig) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg("error.invalidSuperview"));
        }

        // Superview configured but solution does not use UNDER
        if (hasUnderSuperviewConfig && !solutionUsesUnder) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg("error.invalidSuperview"));
        }

        // Ref superview configured but solution does not use MAKE_REF
        if (hasRefSuperviewConfig && !solutionUsesMakeRef) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg("error.invalidSuperview"));
        }

        // Validate that UNDER references the correct superview name
        if (hasUnderSuperviewConfig) {
            String expectedSuperviewName = extractViewName(underSuperview);
            if (expectedSuperviewName == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg("error.invalidSuperview"));
            }
            Pattern correctUnder = Pattern.compile(
                "\\bUNDER\\s+" + Pattern.quote(expectedSuperviewName) + "\\b",
                Pattern.CASE_INSENSITIVE
            );
            if (!correctUnder.matcher(solution).find()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg("error.invalidSuperview"));
            }
        }

        // Validate that MAKE_REF references the correct ref superview name
        if (hasRefSuperviewConfig) {
            String expectedRefViewName = extractViewName(refSuperview);
            if (expectedRefViewName == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg("error.invalidSuperview"));
            }
            Pattern correctMakeRef = Pattern.compile(
                "\\bMAKE_REF\\s*\\(\\s*" + Pattern.quote(expectedRefViewName) + "\\s*,",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
            );
            if (!correctMakeRef.matcher(solution).find()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg("error.invalidSuperview"));
            }
        }

        // OID is only required when UNDER is not used
        if (!hasUnderSuperviewConfig) {
            Matcher oidMatcher = OID_PATTERN.matcher(solution);
            if (!oidMatcher.find()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg("error.invalidOid"));
            }
            String oidCols = oidMatcher.group(1).trim();
            if (oidCols.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg("error.invalidOid"));
            }
            task.setExpectedIdentifier(oidCols);
        } else {
            task.setExpectedIdentifier(null);
        }

        task.setSolution(solution);
        task.setTestQuery(testQuery);
        task.setUnderSuperview(underSuperview);
        task.setRefSuperview(refSuperview);
        task.setMissingPrimitiveFieldPenalty(data.missingPrimitiveFieldPenalty());
        task.setMissingObjectFieldPenalty(data.missingObjectFieldPenalty());
        task.setMissingNestedTablePenalty(data.missingNestedTablePenalty());
        task.setWrongNestedTableTypePenalty(data.wrongNestedTableTypePenalty());
        task.setWrongViewObjectTypePenalty(data.wrongViewObjectTypePenalty());
        task.setWrongOidPenalty(data.wrongOidPenalty());
        task.setWrongContentPenalty(data.wrongContentPenalty());
        task.setWrongColumnOrderPenalty(data.wrongColumnOrderPenalty());
        task.setWrongSuperviewPenalty(data.wrongSuperviewPenalty());
    }

    private void validateSolutionSemicolon(String solution) {
        if (solution == null || !solution.trim().endsWith(";")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg("error.missingSemicolon"));
        }
    }

    private String extractViewName(String ddl) {
        if (ddl == null) return null;
        Matcher matcher = VIEW_NAME_PATTERN.matcher(ddl);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String msg(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }

    @Override
    protected TaskModificationResponseDto mapToReturnData(OrViewTask task, boolean create) {
        return new TaskModificationResponseDto(null, null);
    }
}
