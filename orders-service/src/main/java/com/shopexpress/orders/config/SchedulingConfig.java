package com.shopexpress.orders.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Activa @Scheduled para SagaRecoveryJob. Viene apagado por defecto en Spring.
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
