package com.example.appShop;

import com.example.appShop.config.AppMailProperties;
import com.example.appShop.config.VerificationProperties;
import com.example.appShop.repositories.PromoCodeRepository;
import com.example.appShop.rest.AdminPromoCodeController;
import com.example.appShop.rest.AdminTShirtController;
import com.example.appShop.rest.FavoriteController;
import com.example.appShop.rest.PromoCodeController;
import com.example.appShop.services.EmailSender;
import com.example.appShop.services.EmailVerificationService;
import com.example.appShop.services.FavoriteService;
import com.example.appShop.services.PromoCodeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверка «сборки» всего приложения без MySQL: подъём контейнера на in-memory H2
 * в режиме MySQL (ddl-auto=create-drop, база живёт только внутри теста).
 *
 * Чем этот тест отличается от AppShopRunTest (который требует настоящий MySQL):
 * он ловит ошибки сборки контекста — «не найден бин», «два бина одного типа»,
 * «не прочитался @ConfigurationProperties» — не трогая рабочую базу разработчика.
 * Именно так можно проверить, что @ConfigurationPropertiesScan подхватил новые
 * VerificationProperties и AppMailProperties, а новые контроллеры и сервисы
 * избранных/промокодов попали в компонентный скан.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:context-smoke;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        // SMTP в тесте заведомо нет: проверяем, что EmailSender это честно сообщает
        "spring.mail.host="
})
class ApplicationContextSmokeTest {

    @Autowired
    private ConfigurableApplicationContext context;

    @Test
    void newBeansAreWiredIntoTheContainer() {
        // по одному бину на класс: дубликаты (или, наоборот, отсутствие бина) уронили бы старт
        assertThat(context.getBeanNamesForType(PromoCodeService.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(FavoriteService.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(EmailVerificationService.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(EmailSender.class)).hasSize(1);
    }

    @Test
    void newControllersAreVisibleToSpringMvc() {
        assertThat(context.getBeanNamesForType(AdminTShirtController.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(AdminPromoCodeController.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(PromoCodeController.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(FavoriteController.class)).hasSize(1);
    }

    @Test
    void configurationPropertiesRecordsAreRegistered() {
        // @ConfigurationPropertiesScan должен подобрать новые записи: без них конструкторы
        // EmailSender и EmailVerificationService не собрались бы, и контекст бы не стартовал
        VerificationProperties verification = context.getBean(VerificationProperties.class);
        assertThat(verification.isEnabledOrDefault()).isTrue();
        assertThat(verification.codeTtlOrDefault()).isNotNull();

        AppMailProperties mail = context.getBean(AppMailProperties.class);
        assertThat(mail.fromOrDefault()).isNotBlank();
    }

    @Test
    void smtpIsReportedAsNotConfiguredSoPinGoesToLog() {
        // при пустом spring.mail.host JavaMailSender не создаётся, и PIN-код должен
        // печататься в лог — этот путь обязан работать и в тесте, и у разработчика
        assertThat(context.getBean(EmailSender.class).isSmtpConfigured()).isFalse();
    }

    @Test
    void promoCodesTableWasCreatedAndIsReadable() {
        PromoCodeRepository repository = context.getBean(PromoCodeRepository.class);
        assertThat(repository.count()).isZero();
    }
}
