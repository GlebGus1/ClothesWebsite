package com.example.appShop.services;

import com.example.appShop.dto.PromoCodeDto;
import com.example.appShop.dto.PromoCodeFormRequest;
import com.example.appShop.dto.PromoValidationDto;
import com.example.appShop.dto.PromoValidationRequest;
import com.example.appShop.dto.TotalsDto;
import com.example.appShop.entities.PromoCode;
import com.example.appShop.entities.TShirt;
import com.example.appShop.exceptions.ConflictException;
import com.example.appShop.exceptions.InvalidRequestException;
import com.example.appShop.exceptions.NotFoundException;
import com.example.appShop.mapper.PromoCodeMapper;
import com.example.appShop.repositories.PromoCodeRepository;
import com.example.appShop.repositories.TShirtRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;

/**
 * Промокоды: админ их создаёт/выключает, покупатель применяет в корзине.
 *
 * Два разных сценария чтения:
 *  - admin CRUD (list/create/update/delete) — весь код любой, как его завели;
 *  - покупательская проверка (validate/resolveOrNull) — код обязан существовать,
 *    быть активным и не просроченным. Именно resolveOrNull вызывается из
 *    OrderService.create: скидку нельзя «придумать» на фронтенде, её подтверждает сервер.
 */
@Service
public class PromoCodeService {

    /** Алфавит без похожих друг на друга символов (O/0, I/1): код диктуют по телефону. */
    private static final char[] GENERATION_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int GENERATED_LENGTH = 8;
    private static final int MAX_GENERATION_ATTEMPTS = 20;

    private final PromoCodeRepository promoCodeRepository;
    private final TShirtRepository tShirtRepository;
    private final PromoCodeMapper promoCodeMapper;
    private final SecureRandom random = new SecureRandom();

    public PromoCodeService(PromoCodeRepository promoCodeRepository,
                            TShirtRepository tShirtRepository,
                            PromoCodeMapper promoCodeMapper) {
        this.promoCodeRepository = promoCodeRepository;
        this.tShirtRepository = tShirtRepository;
        this.promoCodeMapper = promoCodeMapper;
    }

    @Transactional(readOnly = true)
    public List<PromoCodeDto> list() {
        return promoCodeRepository.findAllByOrderByIdDesc().stream()
                .map(promoCodeMapper::toDto)
                .toList();
    }

    @Transactional
    public PromoCodeDto create(PromoCodeFormRequest form) {
        String code = form.codeOrEmpty().isBlank() ? generateCode() : normalize(form.codeOrEmpty());
        if (promoCodeRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("Такой промокод уже есть: " + code);
        }

        PromoCode promoCode = new PromoCode();
        applyForm(promoCode, code, form);
        return promoCodeMapper.toDto(promoCodeRepository.save(promoCode));
    }

    @Transactional
    public PromoCodeDto update(long id, PromoCodeFormRequest form) {
        PromoCode promoCode = promoCodeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Промокод", id));

        String code = form.codeOrEmpty().isBlank() ? promoCode.getCode() : normalize(form.codeOrEmpty());
        boolean codeChanged = !code.equalsIgnoreCase(promoCode.getCode());
        if (codeChanged && promoCodeRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("Такой промокод уже есть: " + code);
        }

        applyForm(promoCode, code, form);
        return promoCodeMapper.toDto(promoCodeRepository.save(promoCode));
    }

    /**
     * Удаляем жёстко: заказы хранят строку-«слепок» кода и процент скидки, поэтому
     * внешний ключ нам не мешает, а история заказа не пострадает.
     */
    @Transactional
    public void delete(long id) {
        PromoCode promoCode = promoCodeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Промокод", id));
        promoCodeRepository.delete(promoCode);
    }

    /**
     * Проверка кода покупателем (поле «промокод» в корзине/оформлении).
     * Возвращает пересчитанные сервером суммы — их и показываем в UI.
     */
    @Transactional(readOnly = true)
    public PromoValidationDto validate(PromoValidationRequest request) {
        PromoCode promoCode = requireUsable(request.code());
        TotalsDto totals = TotalsDto.of(subtotalOf(request.tshirtIds()), promoCode.getDiscountPercent());

        return new PromoValidationDto(
                promoCode.getCode(),
                promoCode.getDiscountPercent(),
                promoCode.getExpiresAt(),
                totals,
                "Промокод %s применён: скидка %d%%".formatted(promoCode.getCode(), promoCode.getDiscountPercent()));
    }

    /**
     * Код из заказа: пустой — возвращаем null (скидки нет), непустой и неприменимый —
     * бросаем InvalidRequestException, чтобы покупатель увидел честную причину, а не
     * «молча» применённую полную сумму.
     */
    @Transactional(readOnly = true)
    public PromoCode resolveOrNull(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return null;
        }
        return requireUsable(rawCode);
    }

    private PromoCode requireUsable(String rawCode) {
        String code = normalize(rawCode);
        PromoCode promoCode = promoCodeRepository.findFirstByCodeIgnoreCase(code)
                .orElseThrow(() -> new InvalidRequestException("Промокод «" + rawCode.trim() + "» не найден"));

        if (!promoCode.isActive()) {
            throw new InvalidRequestException("Промокод «" + promoCode.getCode() + "» выключен администратором");
        }
        if (promoCode.isExpiredAt(Instant.now())) {
            throw new InvalidRequestException("Срок действия промокода «" + promoCode.getCode() + "» истёк");
        }
        return promoCode;
    }

    /** Сумма корзины по ценам из базы (фронтендовым ценам не доверяем). */
    private double subtotalOf(List<Long> tshirtIds) {
        List<TShirt> products = tShirtRepository.findAllById(tshirtIds);
        if (products.isEmpty()) {
            throw new NotFoundException("Ни одного товара из корзины не найдено");
        }
        return products.stream().mapToDouble(tShirt -> tShirt.getPrice() == null ? 0.0 : tShirt.getPrice()).sum();
    }

    private void applyForm(PromoCode promoCode, String code, PromoCodeFormRequest form) {
        promoCode.setCode(code);
        promoCode.setDiscountPercent(form.discountPercent());
        promoCode.setActive(form.isActive());
        promoCode.setExpiresAt(form.expiresAt());
    }

    private String normalize(String code) {
        return code.trim().toUpperCase();
    }

    private String generateCode() {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String candidate = randomCode();
            if (!promoCodeRepository.existsByCodeIgnoreCase(candidate)) {
                return candidate;
            }
        }
        throw new InvalidRequestException("Не удалось сгенерировать уникальный код, впишите свой");
    }

    private String randomCode() {
        StringBuilder code = new StringBuilder(GENERATED_LENGTH);
        for (int i = 0; i < GENERATED_LENGTH; i++) {
            code.append(GENERATION_ALPHABET[random.nextInt(GENERATION_ALPHABET.length)]);
        }
        return code.toString();
    }
}
