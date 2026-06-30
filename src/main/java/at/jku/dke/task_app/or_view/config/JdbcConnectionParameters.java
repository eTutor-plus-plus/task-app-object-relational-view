package at.jku.dke.task_app.or_view.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Oracle JDBC connections used by the ORV module.
 */
@ConfigurationProperties(prefix = "jdbc")
public record JdbcConnectionParameters(
    String url,
    AdminCredentials admin,
    ExecutorCredentials executor,
    int maxPoolSize,
    long maxLifetime,
    long connectionTimeout
) {
    /** Credentials for the admin database connection. */
    public record AdminCredentials(String username, String password) {}

    /** Credentials for the executor database connection. */
    public record ExecutorCredentials(String username, String password) {}
}
