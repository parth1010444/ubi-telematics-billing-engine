package com.example.ubi;

import com.example.ubi.config.StripeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(StripeProperties.class)
public class UbiTelematicsBillingEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(UbiTelematicsBillingEngineApplication.class, args);
    }
}
