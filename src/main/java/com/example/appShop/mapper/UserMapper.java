package com.example.appShop.mapper;

import com.example.appShop.dto.UserDto;
import com.example.appShop.entities.User;

/** Entity User -> UserDto. Единственное место, где мы решаем, какие поля пользователя видно наружу. */
public final class UserMapper {

    private UserMapper() {
    }

    public static UserDto toDto(User user) {
        return new UserDto(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.isAdmin());
    }
}
