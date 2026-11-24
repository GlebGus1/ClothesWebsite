package com.example.appShop.services;

import com.example.appShop.config.VerificationProperties;
import com.example.appShop.entities.User;
import com.example.appShop.exceptions.InvalidRequestException;
import com.example.appShop.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;

/**
 * PIN-код подтверждения e-mail.
 *
 * Сценарий:
 *   1) регистрация -> аккаунт создаётся enabled=false, ему выдаётся 6-значный PIN;
 *   2) PIN уходит письмом (EmailSender). Письма ходить некуда (SMTP не настроен) ->
 *      PIN печатается в лог бэкенда строкой «PIN-код подтверждения регистрации»;
 *   3) /api/auth/verify-email сверяет код, ставит enabled=true, код стирает;
 *   4) пока enabled=false, вход невозможен: DaoAuthenticationProvider видит
 *      UserDetails.isEnabled()==false и не пропускает логин (см. UserPrincipal).
 *
 * Код генерируется SecureRandom (не java.util.Random): PIN должен быть
 * непредсказуемым, иначе подбор кода стал бы тривиальным.
 */
@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);
    private static final int CODE_LENGTH = 6;

    private final UserRepository userRepository;
    private final EmailSender emailSender;
    private final VerificationProperties properties;
    private final SecureRandom random = new SecureRandom();

    public EmailVerificationService(UserRepository userRepository,
                                    EmailSender emailSender,
                                    VerificationProperties properties) {
        this.userRepository = userRepository;
        this.emailSender = emailSender;
        this.properties = properties;
    }

    /** false — подтверждение отключено конфигом, аккаунт активен сразу после регистрации. */
    public boolean isVerificationRequired() {
        return properties.isEnabledOrDefault();
    }

    /** Выдаёт новый PIN, сохраняет его и отправляет (или печатает в лог). */
    @Transactional
    public String issueCode(User user) {
        String code = randomSixDigits();

        user.setVerificationCode(code);
        user.setVerificationCodeSentAt(Instant.now());
        user.setEnabled(false);
        userRepository.save(user);

        boolean sent = emailSender.send(user.getEmail(),
                "Подтверждение регистрации в ClothesShop",
                emailText(user, code));
        if (!sent) {
            log.warn("PIN-код подтверждения регистрации для {} (введите его в форме «Подтверждение e-mail»): {}",
                    user.getEmail(), code);
        }
        return code;
    }

    /** Активирует аккаунт без письма — режим app.verification.enabled=false. */
    @Transactional
    public void activate(User user) {
        user.setEnabled(true);
        user.setVerificationCode(null);
        user.setVerificationCodeSentAt(null);
        userRepository.save(user);
    }

    /**
     * Сверяет введённый код. Сообщения сформулированы так, чтобы не подсказывать
     * злоумышленнику, был ли код выдан раньше или просто введён неверно.
     */
    @Transactional
    public void confirm(User user, String rawCode) {
        if (user.isEnabled()) {
            throw new InvalidRequestException("Этот e-mail уже подтверждён — просто войдите");
        }

        String stored = user.getVerificationCode();
        if (stored == null || stored.isBlank()) {
            throw new InvalidRequestException("Для этого e-mail код не выдавался. Запросите новый PIN-код");
        }
        if (isExpired(user)) {
            throw new InvalidRequestException("PIN-код просрочен. Запросите новый");
        }
        if (!stored.equals(normalizeCode(rawCode))) {
            throw new InvalidRequestException("Неверный PIN-код");
        }

        activate(user);
        log.info("E-mail {} подтверждён пользователем id={}", user.getEmail(), user.getId());
    }

    public boolean hasPendingCode(User user) {
        return user.getVerificationCode() != null && !user.getVerificationCode().isBlank();
    }

    /** Коды «до 6 цифр» принимаем с пробелами и дефисами — так его удобнее вводить с телефона. */
    public static String normalizeCode(String rawCode) {
        return rawCode == null ? "" : rawCode.replaceAll("[\\s-]", "").trim();
    }

    private boolean isExpired(User user) {
        Instant sentAt = user.getVerificationCodeSentAt();
        if (sentAt == null) {
            // Старые записи без отметки времени считаем не просроченными:
            // пользователь сможет запросить новый код и активировать аккаунт.
            return false;
        }
        return sentAt.plus(properties.codeTtlOrDefault()).isBefore(Instant.now());
    }

    private String randomSixDigits() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }

    private String emailText(User user, String code) {
        long hours = Math.max(1, properties.codeTtlOrDefault().toHours());
        return """
                Привет, %s %s!

                Спасибо за регистрацию в ClothesShop.
                Ваш PIN-код для подтверждения e-mail: %s

                Код действует %d ч. Вход в магазин откроется сразу после его ввода.
                Если вы ничего не регистрировали — просто проигнорируйте это письмо.
                """.formatted(user.getFirstName(), user.getLastName(), code, hours);
    }
}
