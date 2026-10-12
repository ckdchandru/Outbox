package com.ob;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class ObApplication {

    public static void main(String[] args) {
        // timezone
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(ObApplication.class, args);
    }
}
