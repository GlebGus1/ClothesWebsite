package com.example.appShop.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Аккаунт внесён в «чёрный список» админом (users.banned = true) — 403 Forbidden.
 *
 * Отдельное исключение нужно, чтобы:
 *  - вернуть именно 403 (а не 401, как при неверном пароле): учётные данные-то
 *    правильные, но доступ закрыт;
 *  - не путать его с AccountNotVerifiedException (403 «e-mail не подтверждён»),
 *    где фронтенду надо показать форму ввода PIN-кода, а не запрет.
 */
public class AccountBannedException extends ApiException {

    /** Текст один на весь проект: его же показывает фильтр JWT, который не может бросать исключения. */
    public static final String MESSAGE = "Аккаунт заблокирован. Обратитесь к администратору сервиса.";

    public AccountBannedException() {
        super(HttpStatus.FORBIDDEN, MESSAGE);
    }
}
