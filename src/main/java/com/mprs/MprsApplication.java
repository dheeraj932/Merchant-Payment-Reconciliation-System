package com.mprs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Entry Point for the Merchant Payout Reconciliation System (MPRS).
 */

@SpringBootApplication
@EnableAsync
public class MprsApplication {
    public static void main(String[] args) {
        SpringApplication.run(MprsApplication.class, args);
    }
}
