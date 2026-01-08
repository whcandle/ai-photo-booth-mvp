package com.mg.booth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AiPhotoBoothApplication {
  public static void main(String[] args) {
    SpringApplication.run(AiPhotoBoothApplication.class, args);
  }
}
