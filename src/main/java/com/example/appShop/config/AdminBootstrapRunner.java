package com.example.appShop.config;

import com.example.appShop.services.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Небольшой «посевной» шаг: если в конфиге задан app.admin.email и такого пользователя
 * ещё нет — заводим администратора. Это удобнее, чем руками менять is_admin в MySQL.
 *
 * Ничего не удаляет и не пересоздаёт: если пользователь есть — просто выходим.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final AdminBootstrapProperties properties;
    private final UserService userService;

    public AdminBootstrapRunner(AdminBootstrapProperties properties, UserService userService) {
        this.properties = properties;
        this.userService = userService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            log.info("Авто-создание администратора выключено (app.admin.email не задан)");
            return;
        }
        String email = properties.email().trim().toLowerCase();
        if (userService.existsByEmail(email)) {
            log.info("Пользователь {} уже существует — ничего не делаем", email);
            return;
        }
        userService.createAdmin(email, properties.password(),
                properties.firstName() == null ? "Admin" : properties.firstName(),
                properties.lastName() == null ? "Shop" : properties.lastName());
        log.warn("Создан администратор {}. Смените пароль после первого входа!", email);
    }
}
