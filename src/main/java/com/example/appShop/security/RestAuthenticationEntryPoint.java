package com.example.appShop.security;

import com.example.appShop.dto.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Отвечает за случай «запрос зашёл на защищённый URL без токена» (401 в JSON).
 *
 * По умолчанию Spring Security отдавал бы HTML-страницу входа и Set-Cookie: JSESSIONID,
 * то есть снова появились бы сессии. Этот компонент ломает эту логику: только JSON, без куки.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    public static final String ATTR_JWT_ERROR = "jwtErrorMessage";

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        Object filterMessage = request.getAttribute(ATTR_JWT_ERROR);
        String message = filterMessage == null
                ? "Нужен заголовок Authorization: Bearer <access-токен>"
                : filterMessage.toString();

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ApiError.of(HttpStatus.UNAUTHORIZED, message, request.getRequestURI()));
    }
}
