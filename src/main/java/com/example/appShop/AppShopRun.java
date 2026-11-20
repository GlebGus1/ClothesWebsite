package com.example.appShop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan          // подхватывает JwtProperties, CorsProperties, AdminBootstrapProperties
@EnableJpaRepositories
@EnableScheduling                     // включает @Scheduled (чистка просроченных refresh-токенов)
public class AppShopRun {

    public static void main(String[] args) {
        SpringApplication.run(AppShopRun.class, args);
    }
}
