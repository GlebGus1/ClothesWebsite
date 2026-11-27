package com.example.appShop.rest;

import com.example.appShop.dto.BanRequest;
import com.example.appShop.dto.UserDto;
import com.example.appShop.security.UserPrincipal;
import com.example.appShop.services.UserAdminService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Пользователи в админке: список (e-mail, имя, роль, статус) и блокировка в один клик.
 *
 * Пароли и служебные legacy-поля (is_logged, verification_code) наружу не уходят —
 * только то, что описано в UserDto.
 *
 * Кого именно banning касается и почему нельзя забанить себя — в UserAdminService.
 * Роль проверять тут не нужно: весь путь /api/admin/** закрыт ролью ADMIN в SecurityConfig.
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserAdminService userAdminService;

    public AdminUserController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    public List<UserDto> list() {
        return userAdminService.listUsers();
    }

    /**
     * Заблокировать / разблокировать аккаунт.
     * Тело: {"banned": true} или {"banned": false}.
     *
     * actingAdminId берём из access-токена (а не из тела запроса): так админ не сможет
     * «случайно» передать свой id и остаться с доступом к админке.
     */
    @PatchMapping("/{id}/ban")
    public UserDto setBanned(@PathVariable long id,
                             @Valid @RequestBody BanRequest request,
                             @AuthenticationPrincipal UserPrincipal principal) {
        return userAdminService.setBanned(id, request.isBanned(), principal.getId());
    }
}
