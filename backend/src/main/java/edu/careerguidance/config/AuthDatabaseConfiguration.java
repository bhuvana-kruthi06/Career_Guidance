package edu.careerguidance.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@ConditionalOnProperty(prefix = "career-guidance.auth", name = "enabled", havingValue = "true")
public class AuthDatabaseConfiguration {
    @Bean
    public DataSource authDataSource(
            @Value("${DATABASE_URL:jdbc:mysql://127.0.0.1:3306/ai_career_guidance?useSSL=false&serverTimezone=UTC}") String url,
            @Value("${DATABASE_USERNAME:career_guidance}") String username,
            @Value("${DATABASE_PASSWORD:}") String password) {
        return DataSourceBuilder.create().url(url).username(username).password(password).build();
    }

    @Bean
    public JdbcTemplate authJdbcTemplate(DataSource authDataSource) { return new JdbcTemplate(authDataSource); }

    @Bean
    public PlatformTransactionManager authTransactionManager(DataSource authDataSource) {
        return new DataSourceTransactionManager(authDataSource);
    }
}
