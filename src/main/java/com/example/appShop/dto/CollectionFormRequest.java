package com.example.appShop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Форма создания/правки коллекции в админке.
 *
 * badgeColor проверяется регуляркой: в UI этот цвет попадёт прямо в CSS
 * (`style={{borderColor: ...}}`), поэтому «цвет» должен быть именно HEX-строкой,
 * а не произвольным текстом — иначе через поле имени легко было бы засунуть
 * что угодно в атрибут style.
 */
public record CollectionFormRequest(
        @NotBlank(message = "Укажите название коллекции")
        @Size(max = 80, message = "Название не длиннее 80 символов")
        String name,

        @Size(max = 1000, message = "Описание не длиннее 1000 символов")
        String description,

        @Pattern(regexp = "^#?[0-9a-fA-F]{6}$", message = "Цвет бейджа должен быть в формате #RRGGBB")
        String badgeColor,

        Boolean active
) {
    public boolean isActiveOrDefault() {
        return active == null || active;
    }

    /** Приводим цвет к каноничному виду "#RRGGBB" (upper), пустое значение = null. */
    public String normalizedBadgeColor() {
        if (badgeColor == null || badgeColor.isBlank()) {
            return null;
        }
        String value = badgeColor.trim();
        if (!value.startsWith("#")) {
            value = "#" + value;
        }
        return value.toUpperCase();
    }
}
