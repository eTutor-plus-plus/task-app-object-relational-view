package at.jku.dke.task_app.or_view.services;

import java.sql.SQLException;

/**
 * Interface for managing temporary Oracle schemas used during evaluation.
 */
public interface OrViewSchemaService extends AutoCloseable {

    /**
     * Initializes a connection for the given schema.
     */
    void initForSchema(String schemaName) throws SQLException;

    /**
     * Creates a temporary Oracle schema with required privileges.
     */
    void createSchema(String schemaName) throws SQLException;

    /**
     * Drops a temporary Oracle schema and all its objects.
     */
    void dropSchema(String schemaName) throws SQLException;

    /**
     * Executes SQL statements in the given schema.
     */
    void executeStatements(String schemaName, String sql) throws SQLException;

    /**
     * Commits the current transaction.
     */
    void commit() throws SQLException;

    /**
     * Rolls back the current transaction.
     */
    void rollback() throws SQLException;

    @Override
    void close() throws SQLException;
}
