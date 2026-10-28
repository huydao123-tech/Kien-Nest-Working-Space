package com.cowork;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CoworkBookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoworkBookingApplication.class, args);
    }
}
