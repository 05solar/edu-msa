package com.edu.msa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EduMsaApplication {
    public static void main(String[] args) {
        SpringApplication.run(EduMsaApplication.class, args);
    }
}
