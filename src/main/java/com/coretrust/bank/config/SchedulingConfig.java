package com.coretrust.bank.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables @Scheduled methods (the outbox publisher now; the cleanup job later). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}