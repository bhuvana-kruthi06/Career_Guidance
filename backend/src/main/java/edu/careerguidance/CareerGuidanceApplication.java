package edu.careerguidance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class CareerGuidanceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CareerGuidanceApplication.class, args);
    }
}
