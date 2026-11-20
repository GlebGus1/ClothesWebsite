package com.example.appShop.security;

/**
 * Единые названия ролей. Использование констант вместо строк-литералов
 * защищает от опечаток ("ROLE_ADIMN") и позволяет менять роли в одном месте.
 */
public final class Roles {

    public static final String USER = "ROLE_USER";
    public static final String ADMIN = "ROLE_ADMIN";

    public static final String SCOPE_ADMIN = "ADMIN";

    private Roles() {
    }
}
