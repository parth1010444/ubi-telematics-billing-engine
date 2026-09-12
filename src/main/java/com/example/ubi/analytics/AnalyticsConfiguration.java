package com.example.ubi.analytics;

import com.example.ubi.analytics.catalog.SchemaCatalog;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AnalyticsConfiguration {

    @Bean
    public SchemaCatalog schemaCatalog() {
        return SchemaCatalog.builtin();
    }
}
