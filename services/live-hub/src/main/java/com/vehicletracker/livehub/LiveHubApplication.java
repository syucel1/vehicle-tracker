package com.vehicletracker.livehub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LiveHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(LiveHubApplication.class, args);
    }
}
