package com.example.appShop.rest;

import com.example.appShop.dto.UserDto;
import com.example.appShop.mapper.UserMapper;
import com.example.appShop.services.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Список пользователей для админа. Пароли и служебные legacy-поля (is_logged,
 * verification_code) наружу не уходят — только то, что описано в UserDto.
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserDto> list() {
        return userService.findAll().stream().map(UserMapper::toDto).toList();
    }
}
