package com.ec7205.event_hub.auth_service_api.messaging;

import com.ec7205.event_hub.auth_service_api.messaging.dto.AuthNotificationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthNotificationEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${eventhub.notification.auth-queue:auth.notification.queue}")
    private String authNotificationQueue;

    public void publish(AuthNotificationEvent event) {
        rabbitTemplate.convertAndSend(authNotificationQueue, event);
    }
}
