package com.ec7205.event_hub.auth_service_api.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqNotificationConfig {

    @Value("${eventhub.notification.auth-queue:auth.notification.queue}")
    private String authNotificationQueue;

    @Bean
    public Queue authNotificationQueue() {
        return new Queue(authNotificationQueue, true);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
