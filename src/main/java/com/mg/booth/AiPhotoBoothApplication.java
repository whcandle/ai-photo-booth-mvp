package com.mg.booth;

import com.mg.booth.config.BoothProps;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(BoothProps.class)
public class AiPhotoBoothApplication {
  public static void main(String[] args) {
    SpringApplication.run(AiPhotoBoothApplication.class, args);
  }
}
