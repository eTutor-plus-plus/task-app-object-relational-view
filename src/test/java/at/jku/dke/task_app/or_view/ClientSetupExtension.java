package at.jku.dke.task_app.or_view;

import at.jku.dke.etutor.task_app.auth.AuthConstants;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configures API keys for OR-View module tests.
 */
public class ClientSetupExtension implements BeforeAllCallback {

    private static final Logger LOG = LoggerFactory.getLogger(ClientSetupExtension.class);

    public static final String CRUD_API_KEY = "orv-crud-api-key";
    public static final String SUBMIT_API_KEY = "orv-submit-api-key";
    public static final String READ_API_KEY = "orv-read-api-key";

    /**
     * Creates a new instance of class {@link ClientSetupExtension}.
     */
    public ClientSetupExtension() {
    }

    /**
     * Configures the API keys for the OR-View tests.
     *
     * @param extensionContext The extension context.
     */
    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        LOG.info("Configuring OR-View API keys ...");

        System.setProperty("clients.api-keys[0].name", "orv-admin");
        System.setProperty("clients.api-keys[0].key", CRUD_API_KEY);
        System.setProperty("clients.api-keys[0].roles[0]", AuthConstants.CRUD);

        System.setProperty("clients.api-keys[1].name", "orv-moodle-submit");
        System.setProperty("clients.api-keys[1].key", SUBMIT_API_KEY);
        System.setProperty("clients.api-keys[1].roles[0]", AuthConstants.SUBMIT);

        System.setProperty("clients.api-keys[2].name", "orv-moodle-read");
        System.setProperty("clients.api-keys[2].key", READ_API_KEY);
        System.setProperty("clients.api-keys[2].roles[0]", AuthConstants.READ_SUBMISSION);

        System.setProperty("logging.level.at.jku.dke", "DEBUG");
    }
}
