package com.example.appShop.rest;

import com.example.appShop.dto.AnalyticsDashboardDto;
import com.example.appShop.dto.PromoCodeUsageDto;
import com.example.appShop.services.AnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Дашборд и отчёты для администратора: выручка/скидки, воронка продаж,
 * топ просмотренных и топ добавленных в избранное товаров, использование промокодов.
 *
 * Доступ: весь путь /api/admin/** закрыт ролью ADMIN в SecurityConfig — здесь
 * проверок прав нет намеренно (одно место, где правила доступа, меньше и читаемее).
 */
@RestController
@RequestMapping("/api/admin/analytics")
public class AdminAnalyticsController {

    private final AnalyticsService analyticsService;

    public AdminAnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/dashboard")
    public AnalyticsDashboardDto dashboard() {
        return analyticsService.dashboard();
    }

    @GetMapping("/promo-usage")
    public List<PromoCodeUsageDto> promoUsage() {
        return analyticsService.promoCodeUsage();
    }
}
