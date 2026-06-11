package at.jku.dke.task_app.or_view.services;

import at.jku.dke.task_app.or_view.evaluation.OrViewDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class OrViewSchemaServiceImpl implements OrViewSchemaService {

    private static final Logger LOG = LoggerFactory.getLogger(OrViewSchemaServiceImpl.class);

    private final OrViewDataSource dataSource;
    private Connection currentConnection;

    public OrViewSchemaServiceImpl(OrViewDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void initForSchema(String schemaName) throws SQLException {
        if (this.currentConnection != null) {
            throw new SQLException("Service already initialized. Close it first.");
        }
        this.currentConnection = dataSource.connectAdmin();
        this.currentConnection.setAutoCommit(false);
    }

    @SuppressWarnings("SqlSourceToSinkFlow")
    @Override
    public void createSchema(String schemaName) throws SQLException {
        ensureInitialized();

        String user = schemaName.toUpperCase();
        String password = "TempPass123";

        try (Statement stmt = currentConnection.createStatement()) {

            try {
                stmt.execute("DROP USER " + user + " CASCADE");
            } catch (SQLException e) {
                if (e.getErrorCode() != 1918) throw e;
            }

            stmt.execute("CREATE USER " + user + " IDENTIFIED BY " + password + " CONTAINER=CURRENT");
            stmt.execute("GRANT CONNECT, RESOURCE TO " + user);
            stmt.execute("GRANT CREATE VIEW TO " + user);
            stmt.execute("GRANT CREATE TYPE TO " + user);
            stmt.execute("GRANT UNLIMITED TABLESPACE TO " + user);

            LOG.info("Schema created: {}", user);
        }
    }

    @SuppressWarnings("SqlSourceToSinkFlow")
    @Override
    public void dropSchema(String schemaName) throws SQLException {
        if (currentConnection == null) return;

        String user = schemaName.toUpperCase();

        try (Statement stmt = currentConnection.createStatement()) {
            try {
                stmt.execute("DROP USER " + user + " CASCADE");
            } catch (SQLException e) {
                if (e.getErrorCode() != 1918) throw e;
            }
        }
    }

    @SuppressWarnings("SqlSourceToSinkFlow")
    @Override
    public void executeStatements(String schemaName, String sql) throws SQLException {
        ensureInitialized();

        if (sql == null || sql.isBlank()) return;

        String user = schemaName.toUpperCase();

        try (Statement stmt = currentConnection.createStatement()) {

            stmt.execute("ALTER SESSION SET CURRENT_SCHEMA = " + user);

            sql = normalize(sql);

            StringBuilder buffer = new StringBuilder();
            boolean isPlsqlBlock = false;

            String[] lines = sql.split("\n");

            for (String rawLine : lines) {

                String line = rawLine.trim();
                if (line.isEmpty()) continue;

                buffer.append(rawLine).append("\n");

                if (startsBlock(line)) {
                    isPlsqlBlock = true;
                }

                if (line.equals("/")) {

                    String statement = clean(buffer);
                    if (!statement.isEmpty()) {
                        executeSingle(stmt, statement, user);
                    }

                    buffer.setLength(0);
                    isPlsqlBlock = false;
                    continue;
                }

                if (!isPlsqlBlock && line.endsWith(";")) {

                    String statement = clean(buffer);
                    if (!statement.isEmpty()) {
                        executeSingle(stmt, statement, user);
                    }

                    buffer.setLength(0);
                }
            }

            String remaining = clean(buffer);
            if (!remaining.isEmpty()) {
                executeSingle(stmt, remaining, user);
            }
        }
    }

    private boolean startsBlock(String line) {
        String u = line.toUpperCase();

        return u.startsWith("CREATE OR REPLACE TYPE")
            || u.startsWith("CREATE TYPE")
            || u.startsWith("CREATE OR REPLACE VIEW")
            || u.startsWith("CREATE OR REPLACE PACKAGE")
            || u.startsWith("CREATE OR REPLACE TRIGGER")
            || u.contains("TYPE BODY");
    }

    private String normalize(String sql) {
        return sql.replace("\r\n", "\n");
    }

    private String clean(StringBuilder buffer) {
        String s = buffer.toString().trim();

        if (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1).trim();
        }

        if (s.endsWith(";")) {
            s = s.substring(0, s.length() - 1).trim();
        }

        return s;
    }

    private void executeSingle(Statement stmt, String sql, String user) throws SQLException {
        if (sql.isEmpty()) return;

        LOG.info("Executing in {}: {}", user, sql.substring(0, Math.min(sql.length(), 80)));

        try {
            stmt.execute(sql);
        } catch (SQLException e) {
            if (sql.toUpperCase().startsWith("DROP")
                && (e.getErrorCode() == 942 || e.getErrorCode() == 4043 || e.getErrorCode() == 1918)) {
                return;
            }
            LOG.error("SQL failed in schema {}: {}", user, sql);
            LOG.error("Oracle error {}: {}", e.getErrorCode(), e.getMessage());
            throw e;
        }
    }

    @Override
    public void commit() throws SQLException {
        if (currentConnection != null) currentConnection.commit();
    }

    @Override
    public void rollback() throws SQLException {
        if (currentConnection != null) currentConnection.rollback();
    }

    @Override
    public void close() throws SQLException {
        if (currentConnection != null) {
            try {
                rollback();
            } finally {
                currentConnection.close();
                currentConnection = null;
            }
        }
    }

    private void ensureInitialized() throws SQLException {
        if (currentConnection == null) {
            throw new SQLException("Service not initialized.");
        }
    }
}
