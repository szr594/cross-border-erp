package com.szr.crosserp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CrossBorderErpApplication {

    public static void main(String[] args) {
        var context = SpringApplication.run(CrossBorderErpApplication.class, args);
        if (context.getEnvironment().matchesProfiles("init-owner")) {
            context.close();
        }
    }
}

