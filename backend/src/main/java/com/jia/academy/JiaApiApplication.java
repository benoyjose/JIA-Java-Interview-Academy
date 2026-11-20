package com.jia.academy;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication
@EnableScheduling
public class JiaApiApplication {
    public static void main(String[] args) { SpringApplication.run(JiaApiApplication.class, args); }
}
