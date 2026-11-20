# Clothes Website — Spring Boot REST API + JWT + React

Учебный интернет-магазин футболок. Раньше это был монолит на Thymeleaf с корзиной в `HttpSession`.
Сейчас это **два независимых приложения**:

| Часть | Технологии | Где |
|---|---|---|
| Бэкенд (REST API) | Java 17, Spring Boot 3.1.2, Spring Security 6, Spring Data JPA, MySQL 8 | `src/main/java/com/example/appShop` |
| Фронтенд (SPA) | React 18 + TypeScript + Vite + Axios + React Router 6 | `frontend/` |

Авторизация — **stateless JWT**: короткоживущий Access-токен + Refresh-токен с ротацией
и хранением хэша в таблице `refresh_tokens`. Роли: `USER`, `ADMIN`.

Старый Thymeleaf-интерфейс и старые контроллеры сохранены в `_legacy_thymeleaf/` (не удалялись).

## Быстрый запуск

### 1. Бэкенд (порт 8080)

```bash
# один раз: скопировать пример секретов и вписать свои значения
cp secrets.properties.example secrets.properties

# запуск (скрипт использует локальный JDK и Maven из scripts/env.sh)
bash scripts/run-backend.sh        # лог: target/backend.log
bash scripts/stop-backend.sh       # остановка по порту 8080
```

Без скриптов (если `java`/`mvn` есть в PATH):

```bash
mvn spring-boot:run
```

Swagger UI: <http://localhost:8080/swagger-ui.html>

Переменные, которые стоит знать:

| Переменная | Назначение |
|---|---|
| `JWT_SECRET` | строка ≥ 64 символов, ключ подписи HS512 (лежит в `secrets.properties`) |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | их использует `scripts/run-backend.sh`, передавая бэкенду `--app.admin.email` / `--app.admin.password` (свойства `app.admin.*` создают админа, если такого e-mail нет) |
| `APP_CORS_ALLOWED_ORIGINS` | список разрешённых источников (по умолчанию `http://localhost:5173`) |

### 2. Фронтенд (порт 5173)

```bash
cd frontend
npm install
npm run dev          # http://localhost:5173
npm run build        # production-сборка в dist/
npm run typecheck    # только проверка типов
```

Во время разработки Vite проксирует `/api` → `http://localhost:8080`, поэтому CORS не мешает.

### 3. Проверки

```bash
bash scripts/mvn.sh test        # 80 тестов (JUnit 5 + Mockito + @SpringBootTest)
bash scripts/smoke-test.sh      # 19 HTTP-сценариев: права, JWT-ротация, multipart, заказы
```

## Основные эндпоинты

| Метод | URL | Кто |
|---|---|---|
| POST | `/api/auth/register`, `/api/auth/login` | все |
| POST | `/api/auth/refresh`, `/api/auth/logout` | владелец refresh-токена |
| GET | `/api/auth/me` | авторизованные |
| GET | `/api/tshirts`, `/api/tshirts/{id}`, `/api/tshirts/sizes`, `/api/images/{id}` | все (гости тоже) |
| POST/PUT/PATCH/DELETE | `/api/tshirts…`, `/api/images/{id}` | ADMIN |
| POST | `/api/orders`, GET `/api/orders/me`, GET `/api/orders/{id}` | авторизованные (свой заказ) |
| GET/PATCH | `/api/admin/orders…`, `/api/admin/users` | ADMIN |

Подробное объяснение архитектуры, JWT и всех решений — в [`REPORT.md`](REPORT.md).
Инструкции по фронтенду — во [`frontend/README.md`](frontend/README.md).
