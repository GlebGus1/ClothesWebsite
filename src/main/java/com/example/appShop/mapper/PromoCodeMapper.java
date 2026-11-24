package com.example.appShop.mapper;

import com.example.appShop.dto.PromoCodeDto;
import com.example.appShop.entities.PromoCode;
import org.springframework.stereotype.Component;

/** Entity PromoCode -> PromoCodeDto. Наружу не отдаём сущность (правило слоёв из AGENTS.md). */
@Component
public class PromoCodeMapper {

    public PromoCodeDto toDto(PromoCode promoCode) {
        return new PromoCodeDto(
                promoCode.getId(),
                promoCode.getCode(),
                promoCode.getDiscountPercent(),
                promoCode.isActive(),
                promoCode.getExpiresAt(),
                promoCode.getCreatedAt());
    }
}
