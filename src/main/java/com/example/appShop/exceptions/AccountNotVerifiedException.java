package com.example.appShop.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Аккаунт создан, но e-mail не подтверждён PIN-кодом (403).
 *
 * Отличаем от 401 «неверный e-mail или пароль»: здесь учётные данные правильные,
 * и фронтенду нужно показать форму ввода кода, а не «неверный пароль».
 */
public class AccountNotVerifiedException extends ApiException {

    public AccountNotVerifiedException() {
        super(HttpStatus.FORBIDDEN,
                "E-mail не подтверждён: введите 6-значный PIN-код, отправленный на почту");
    }
}
