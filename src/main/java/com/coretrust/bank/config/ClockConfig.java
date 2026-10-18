package com.coretrust.bank.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    /** The app's single source of "now". Tests replace it with a fixed clock. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}