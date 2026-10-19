package com.shengid.rednote.config;

import com.shengid.rednote.service.UserService;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class ApplicationConfig {
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
      name = "rednote.admin.initialize",
      havingValue = "true")
  ApplicationRunner administrator(
      UserService users,
      @Value("${rednote.admin.username}") String username,
      @Value("${rednote.admin.password}") String password) {
    return args -> {
      users.initializeAdmin(username, password);
      org.slf4j.LoggerFactory.getLogger(ApplicationConfig.class)
          .info("Rednote administrator initialization completed");
    };
  }
}
