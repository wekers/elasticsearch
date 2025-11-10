package com.wekers.microsb.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.rabbitmq")
public class RabbitMQProperties {
    private Queues queues = new Queues();
    private Exchanges exchanges = new Exchanges();
    private RoutingKeys routingKeys = new RoutingKeys();
    private RetryConfig retry = new RetryConfig();

    @Getter
    @Setter
    public static class Queues {
        private String main;
        private String retry5s;
        private String dead;
    }

    @Getter
    @Setter
    public static class Exchanges {
        private String main;
        private String dlx;
    }

    @Getter
    @Setter
    public static class RoutingKeys {
        private String created;
        private String retry5s;
        private String dead;
    }

    @Getter
    @Setter
    public static class RetryConfig {
        private int maxAttempts = 3;
        private long retryDelayMs = 5000;
    }
}