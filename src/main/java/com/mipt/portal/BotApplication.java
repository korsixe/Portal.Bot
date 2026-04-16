package com.mipt.bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = {"com.mipt.bot", "com.mipt.portal"})
@EntityScan(basePackages = {"com.mipt.portal.entity"})
@EnableJpaRepositories(basePackages = {"com.mipt.portal.repository"})
@EnableScheduling
public class BotApplication {

  public static void main(String[] args) {
    SpringApplication.run(BotApplication.class, args);
  }
}