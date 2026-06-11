package at.jku.dke.task_app.or_view;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * An Oracle container for use in unit tests.
 */
public class AppOracleContainer extends GenericContainer<AppOracleContainer> {

    /**
     * The docker image version of the test database container.
     */
    public static final String IMAGE_VERSION = "gvenzl/oracle-free:23-slim";

    /**
     * The Oracle JDBC port.
     */
    private static final int ORACLE_PORT = 1521;

    /**
     * The Oracle service name.
     */
    private static final String SERVICE_NAME = "FREEPDB1";

    /**
     * The Oracle username.
     */
    private static final String USERNAME = "SYSTEM";

    /**
     * The Oracle password.
     */
    private static final String PASSWORD = "test";

    /**
     * The singleton instance of the test database container.
     */
    public static final AppOracleContainer INSTANCE = new AppOracleContainer();

    /**
     * Creates a new instance of class {@link AppOracleContainer}.
     */
    private AppOracleContainer() {
        super(DockerImageName.parse(IMAGE_VERSION));
        withExposedPorts(ORACLE_PORT);
        // Setzt nur das SYS/SYSTEM Passwort — kein APP_USER um ORA-01920 zu vermeiden
        withEnv("ORACLE_PASSWORD", PASSWORD);
        waitingFor(
            Wait.forLogMessage(".*DATABASE IS READY TO USE.*\\n", 1)
                .withStartupTimeout(Duration.ofMinutes(15))
        );
    }

    /**
     * Returns the JDBC URL for the container.
     *
     * @return The JDBC URL.
     */
    public String getJdbcUrl() {
        return "jdbc:oracle:thin:@//" + getHost() + ":" + getMappedPort(ORACLE_PORT) + "/" + SERVICE_NAME;
    }

    /**
     * Returns the username.
     *
     * @return The username.
     */
    public String getUsername() {
        return USERNAME;
    }

    /**
     * Returns the password.
     *
     * @return The password.
     */
    public String getPassword() {
        return PASSWORD;
    }
}
