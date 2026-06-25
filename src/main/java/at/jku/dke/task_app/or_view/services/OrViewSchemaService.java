package at.jku.dke.task_app.or_view.services;

import java.sql.SQLException;

/**
 * Interface for managing temporary Oracle schemas used during evaluation.
 */
public interface OrViewSchemaService extends AutoCloseable {

    void initForSchema(String schemaName) throws SQLException;

    void createSchema(String schemaName) throws SQLException;

    void dropSchema(String schemaName) throws SQLException;

    void executeStatements(String schemaName, String sql) throws SQLException;

    void commit() throws SQLException;

    void rollback() throws SQLException;

    @Override
    void close() throws SQLException;
}
