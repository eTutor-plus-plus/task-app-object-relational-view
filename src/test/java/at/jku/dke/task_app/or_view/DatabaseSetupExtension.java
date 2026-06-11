package at.jku.dke.task_app.or_view;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configures the database connection for OR-View tests.
 * Connects to the local Oracle database running on localhost:1521.
 */
public class DatabaseSetupExtension implements BeforeAllCallback {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseSetupExtension.class);

    /**
     * Creates a new instance of class {@link DatabaseSetupExtension}.
     */
    public DatabaseSetupExtension() {
    }

    /**
     * Configures the database connection properties for the tests.
     *
     * @param context The extension context.
     */
    @Override
    public void beforeAll(ExtensionContext context) {
        LOG.info("Configuring Database for OR-View Tests...");

        // Container starten (nur einmal dank Singleton)
        AppOracleContainer.INSTANCE.start();

        System.setProperty("spring.test.database.replace", "none");

        // Container-URL statt localhost
        System.setProperty("spring.datasource.url", AppOracleContainer.INSTANCE.getJdbcUrl());
        System.setProperty("spring.datasource.username", AppOracleContainer.INSTANCE.getUsername());
        System.setProperty("spring.datasource.password", AppOracleContainer.INSTANCE.getPassword());
        System.setProperty("spring.datasource.driver-class-name", "oracle.jdbc.OracleDriver");
        System.setProperty("spring.jpa.hibernate.ddl-auto", "none");
        System.setProperty("spring.flyway.enabled", "true");
        System.setProperty("spring.flyway.locations", "classpath:db/migration");
        System.setProperty("spring.flyway.baseline-on-migrate", "true");
        System.setProperty("spring.flyway.baseline-version", "0");

        System.setProperty("jdbc.url", AppOracleContainer.INSTANCE.getJdbcUrl());
        System.setProperty("jdbc.admin.username", AppOracleContainer.INSTANCE.getUsername());
        System.setProperty("jdbc.admin.password", AppOracleContainer.INSTANCE.getPassword());
        System.setProperty("jdbc.executor.username", AppOracleContainer.INSTANCE.getUsername());
        System.setProperty("jdbc.executor.password", AppOracleContainer.INSTANCE.getPassword());
        System.setProperty("jdbc.max-pool-size", "5");
        System.setProperty("jdbc.max-lifetime", "1800000");
        System.setProperty("jdbc.connection-timeout", "10000");

        System.setProperty("logging.level.at.jku.dke", "DEBUG");
    }
}
