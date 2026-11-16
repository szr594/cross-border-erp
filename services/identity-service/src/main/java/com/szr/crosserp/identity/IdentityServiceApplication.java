package com.szr.crosserp.identity;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication
public class IdentityServiceApplication {
    public static void main(String[] args) {
        var context = SpringApplication.run(IdentityServiceApplication.class, args);
        if (context.getEnvironment().matchesProfiles("init-owner")) context.close();
    }
}
