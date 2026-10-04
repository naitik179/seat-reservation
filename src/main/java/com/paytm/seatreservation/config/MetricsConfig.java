package com.paytm.seatreservation.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Gauge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    Counter reservationsConfirmed(MeterRegistry registry) {
        return Counter.builder("reservations_confirmed_total").description("Number of successfully confirmed reservations").register(registry);
    }

    @Bean
    Counter reservationsDeclined(MeterRegistry registry) {
        return Counter.builder("reservations_declined_total").description("Number of declined reservation requests").tag("reason", "unknown").register(registry);
    }
}
