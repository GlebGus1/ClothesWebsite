package com.example.appShop.security;

/** Два типа токенов различаются в claim "type", чтобы access нельзя было использовать как refresh. */
public enum TokenType {
    ACCESS("access"),
    REFRESH("refresh");

    private final String value;

    TokenType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static TokenType from(String raw) {
        for (TokenType type : values()) {
            if (type.value.equals(raw)) {
                return type;
            }
        }
        return null;
    }
}
