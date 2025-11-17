package com.wekers.microsa.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.rabbitmq")
public class RabbitMQProperties {


    private Exchanges exchanges = new Exchanges();
    private RoutingKeys routingKeys = new RoutingKeys();


    @Getter
    @Setter
    public static class Exchanges {
        private String main;

    }

    @Getter
    @Setter
    public static class RoutingKeys {
        private String created;
        private String deleted;
        private String updated;
    }


}
