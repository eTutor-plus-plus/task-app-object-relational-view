package at.jku.dke.task_app.or_view.config;

import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HibernateConfig {

    /**
     * Creates a new instance of class {@link HibernateConfig}.
     */
    public HibernateConfig() {
    }

    /**
     * Customizes Hibernate properties for Oracle compatibility.
     *
     * @return The Hibernate properties customizer.
     */
    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer() {
        return properties -> {
            // Store UUID as CHAR(36) instead of RAW(16)
            properties.put("hibernate.type.preferred_uuid_jdbc_type", "CHAR");
        };
    }
}
