package at.jku.dke.task_app.or_view.evaluation;

import at.jku.dke.task_app.or_view.config.JdbcConnectionParameters;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;

@Service
public class OrViewDataSource implements AutoCloseable {

    private final HikariDataSource adminDataSource;
    private final HikariDataSource executorDataSource;

    public OrViewDataSource(JdbcConnectionParameters parameters) {
        this.adminDataSource = new HikariDataSource(buildAdminConfig(parameters));
        this.executorDataSource = new HikariDataSource(buildExecutorConfig(parameters));
    }

    private static HikariConfig buildAdminConfig(JdbcConnectionParameters parameters) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(parameters.url());
        config.setUsername(parameters.admin().username());
        config.setPassword(parameters.admin().password());
        config.setMaximumPoolSize(2);
        config.setPoolName("orview-admin-pool");
        return config;
    }

    private static HikariConfig buildExecutorConfig(JdbcConnectionParameters parameters) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(parameters.url());
        config.setUsername(parameters.executor().username());
        config.setPassword(parameters.executor().password());
        config.setMaximumPoolSize(parameters.maxPoolSize());
        config.setConnectionTimeout(parameters.connectionTimeout());
        config.setMaxLifetime(parameters.maxLifetime());
        config.setPoolName("orview-executor-pool");
        return config;
    }

    /**
     * Returns a connection with admin privileges (for schema setup).
     */
    public Connection connectAdmin() throws SQLException {
        return adminDataSource.getConnection();
    }

    /**
     * Returns a connection with executor privileges.
     */
    public Connection connectExecutor() throws SQLException {
        return executorDataSource.getConnection();
    }

    @Override
    public void close() {
        if (adminDataSource != null) adminDataSource.close();
        if (executorDataSource != null) executorDataSource.close();
    }
}
