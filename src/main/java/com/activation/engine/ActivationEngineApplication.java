package com.activation.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ActivationEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(ActivationEngineApplication.class, args);
    }
}
