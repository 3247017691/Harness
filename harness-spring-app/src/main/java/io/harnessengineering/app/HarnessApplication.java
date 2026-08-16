package io.harnessengineering.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Spring Boot entry point for the HarnessEngineering application assembly. */
@SpringBootApplication
public class HarnessApplication {
    public static void main(String[] arguments) {
        SpringApplication.run(HarnessApplication.class, arguments);
    }
}
