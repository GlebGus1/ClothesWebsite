package com.example.appShop.rest;

import com.example.appShop.dto.TShirtDto;
import com.example.appShop.services.FavoriteService;
import com.example.appShop.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Избранное пользователя. Доступен только вошедшим (правило в SecurityConfig),
 * а сам пользователь берётся из токена — @AuthenticationPrincipal, а не из параметров.
 *
 * Ответы — 204 без тела: фронтенд и так знает, какое действие делал, и перекрашивает
 * сердечко локально, не дожидаясь JSON.
 */
@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @GetMapping
    public List<TShirtDto> list(@AuthenticationPrincipal UserPrincipal principal) {
        return favoriteService.list(principal);
    }

    /** Лёгкий запрос для каталога: какие id уже отмечены сердечком. */
    @GetMapping("/ids")
    public List<Long> ids(@AuthenticationPrincipal UserPrincipal principal) {
        return favoriteService.favoriteIds(principal);
    }

    @PostMapping("/{tshirtId}")
    public ResponseEntity<Void> add(@PathVariable long tshirtId,
                                    @AuthenticationPrincipal UserPrincipal principal) {
        favoriteService.add(principal, tshirtId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{tshirtId}")
    public ResponseEntity<Void> remove(@PathVariable long tshirtId,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        favoriteService.remove(principal, tshirtId);
        return ResponseEntity.noContent().build();
    }
}
