# plan.md — Переход на REST API + Stateless JWT и React-клиент

> Рабочий документ. После каждого шага (этапа) — проверка сборки и запись в `REPORT.md`.

## 0. Контекст, который выяснен до старта

| Что | Значение |
|---|---|
| Backend | Spring Boot 3.1.2, Java 17 (target), Spring Security 6.1, Spring Data JPA |
| БД | MySQL 8.0 (`appshop`), пользователь `root/root`, `ddl-auto=update`, таблицы: `users`, `tshirts`, `images`, `orders`, `order_tshirts` (все пустые) |
| JDK для сборки | JetBrains Runtime 21: `D:\inteljidea\IntelliJ IDEA 2025.3\jbr` (в PATH нет `java`/`mvn`, используется `mvn` 3.9.3 из `~/.m2/wrapper/dists`) |
| Git | `.git` **нет** → старые Thymeleaf-файлы не удаляем, а переносим в `_legacy_thymeleaf/` |
| Frontend | отсутствует, Node 26 + npm 11 доступны |
| Проблемы текущего кода | пакет `сonfigs`/`сontrollers` написан с **кириллической `с`**; корзина живёт в `HttpSession`; сортировка хранится в полях-одиночках контроллера (`currentSize`) — гонка между запросами; `User.order` и `Order.user` — дублирующие связи; entity отдаются наружу |

## 1. Архитектурные решения (принимаю сам, согласно AGENTS.md)

1. **API-first.** Все эндпоинты под префиксом `/api`. Thymeleaf-шаблонизатор убирается из `pom.xml`, старые JSP-подобные шаблоны и статика переносятся в `_legacy_thymeleaf/`.
2. **JWT:** библиотека `io.jsonwebtoken:jjwt` 0.11.5, алгоритм **HS512**, секрет из конфига (`jwt.secret`, переопределяется `JWT_SECRET`).
   - **Access-токен** — 15 минут, stateless, нигде не хранится на сервере. Claims: `sub` (email), `uid`, `role`, `type=access`.
   - **Refresh-токен** — 7 дней, хранится в таблице `refresh_tokens` **в виде SHA-256-хэша**, с ротацией (старый отзывается при обновлении) и отзывом при logout.
3. **Роли** сохраняются: `ROLE_USER`, `ROLE_ADMIN` (в БД — поле `users.is_admin`, как уже есть).
4. **Слои:** `@RestController` ↔ DTO (records), `@Service`, `@Repository`. Мапперы отдельно (`mapper`), никаких entity в JSON.
5. **Корзина** становится клиентской (React Context + localStorage), т.к. stateless-сервер не хранит сессии. Заказ оформляется `POST /api/orders` со списком `tshirtIds`.
6. **Фильтры/сортировка/пагинация** уходят на сервер: `JpaSpecificationExecutor` + `Pageable` (чиним баг с состоянием в контроллере).
7. **CORS** разрешён для `http://localhost:5173` (Vite) через `CorsConfigurationSource`.
8. **Исключения:** `@RestControllerAdvice` + единый формат ошибки `ApiError`.
9. **Тесты:** JUnit 5 + Mockito (чистые unit без БД) + MockMvc `standaloneSetup` для контроллеров. Файлы < 400 строк.
10. **Frontend:** Vite + React 18 + TypeScript + React Router v6 + Axios. Оба токена держим в `localStorage`
    (вариант выбран как самый простой и объяснимый для SPA), при 401 — интерсептор делает один общий
    `refresh`-запрос (shared promise) и повторяет исходный запрос. Компромисс по безопасности
    (XSS читает `localStorage`) описан в `REPORT.md`, там же — как перевести refresh в `httpOnly`-cookie.

### 1.1 Отклонения от первоначального плана (фиксирую по ходу дела)

| План | Что сделано в итоге | Почему |
|---|---|---|
| `MockMvc`-тесты контроллеров (`AuthControllerTest`, `ApiExceptionHandlerTest`) | Тестируем filter/service-слой: `JwtAuthenticationFilterTest` (реальный `JwtTokenProvider` + `MockHttpServletRequest`), плюс интеграционный `AppShopRunTest` (`@SpringBootTest`) | MockMvc standalone не ловит главную логику безопасности (фильтр + `SecurityFilterChain`), а `@SpringBootTest` с полным MVC требует моков токенов и делает тесты хрупкими. Фильтр + сервисы + контекст-тест дают больше пользы при том же времени |
| `access` в памяти, `refresh` в `localStorage` | Оба в `localStorage` | Один механизм хранения проще и переживает перезагрузку страницы; риск и способ усиления описаны в `REPORT.md` |
| `ProfilePage` | Отдельной страницы нет: профиль = данные из `GET /api/auth/me`, они показаны в шапке; редактирование профиля в API не заводилось | Старый Thymeleaf-UI тоже не давал редактировать профиль; не хотим выдумывать API, которого не было |
| жёсткое `DELETE /api/tshirts/{id}` | Мягкое удаление: `tshirts.disabled=true` + `available=false` | Жёсткий `DELETE` разбивается о внешний ключ `order_tshirts`: товар уже участвует в заказах |
| `sort=price_asc` и т.п. | Белый список: `NEWEST`, `OLDEST`, `PRICE_ASC`, `PRICE_DESC`, `NAME_ASC` (`services/TShirtSort.java`) | Сортировка по произвольной колонке — дыра и лишний запрос в БД |

## 2. Этапы

### Этап 1 — Подготовка и plan/backup
- [x] Изучить проект, БД, окружение.
- [x] Перенести Thymeleaf-шаблоны, статику и старые контроллеры в `_legacy_thymeleaf/` (без Git-истории — сохраняем код).
- [x] Добавить зависимости (`jjwt-api/impl/jackson`, `springdoc-openapi`, validation, `mysql-connector-j`, `spring-security-test`) и `secrets.properties.example`.
- Проверка: `mvn test-compile`. ✅ зелено (плюс приложение реально стартует: `scripts/run-backend.sh`).

### Этап 2 — JWT-инфраструктура
- [x] `config/JwtProperties`, `security/JwtTokenProvider`, `security/JwtAuthenticationFilter`, `security/RestAuthenticationEntryPoint` + `RestAccessDeniedHandler`, `security/UserPrincipal`, `security/TokenHasher`, `security/TokenType`, `security/Roles`, `entities/RefreshToken` + `repositories/RefreshTokenRepository`, `security/RefreshTokenService`, `services/RefreshTokenCleanupJob`.
- Проверка: `mvn test-compile`. ✅

### Этап 3 — Security + конфигурация
- [x] `config/SecurityConfig` (лямбда-синтаксис Security 6), `config/AuthenticationConfig`, `config/CorsProperties`, `config/OpenApiConfig`, `config/AdminBootstrapProperties` + `AdminBootstrapRunner`, `services/CustomUserDetailsService`.
- Проверка: `mvn test-compile` + реальный старт контекста ✅ (`Started AppShopRun`, `GET /api/tshirts` отдаёт JSON без токена).

### Этап 4 — DTO, мапперы, сервисы
- [x] DTO-records: `AuthRequest`, `RegisterRequest`, `RefreshRequest`, `AuthResponse`, `TokenPair`, `UserDto`, `TShirtDto`, `TShirtDetailsDto`, `ImageDto`, `ImageBinary`, `TShirtFormRequest`, `CreateOrderRequest`, `UpdateOrderStatusRequest`, `OrderDto`, `PageResponse`, `ApiError`.
- [x] Мапперы: `UserMapper`, `TShirtMapper`, `OrderMapper`. Спецификации: `specification/TShirtSpecifications`. Утилиты: `services/TShirtSort`, `services/ImageUploadPolicy`.
- [x] Сервисы: `AuthService`, `UserService`, `TShirtService`, `ImageService`, `OrderService`.
- [x] Исключения: `ApiException`, `NotFoundException`, `ConflictException`, `InvalidRequestException`, `InvalidCredentialsException`, `InvalidTokenException`, обработчик `ApiExceptionHandler`.
- Проверка: `mvn test-compile`. ✅

### Этап 5 — REST-контроллеры
- [x] `AuthController` (`/api/auth/register|login|refresh|logout|me`)
- [x] `TShirtController` (`GET /api/tshirts` фильтры+пагинация, `GET /sizes`, `GET /{id}`, `POST`/`PUT` multipart, `PATCH /{id}/preview/{imageId}`, `DELETE /{id}` — мягкое удаление)
- [x] `ImageController` (`GET /api/images/{id}` бинарник, `DELETE /api/images/{id}`)
- [x] `OrderController` (`POST /api/orders`, `GET /api/orders/me`, `GET /api/orders/{id}`)
- [x] `AdminOrderController` (`GET /api/admin/orders`, `PATCH /api/admin/orders/{id}/status`, `GET /api/admin/orders/statuses`), `AdminUserController` (`GET /api/admin/users`)
- [x] Старые `@Controller` перенесены в `_legacy_thymeleaf/`.
- Проверка: `mvn test-compile` + `mvn test` + смоук-скрипт `scripts/smoke-test.sh` (19 сценариев на реальный HTTP). ✅

### Этап 6 — Unit-тесты (AGENTS.md: на каждый сервис/контроллер авторизации)
- [x] `security/JwtTokenProviderTest`, `security/TokenHasherTest`, `security/RefreshTokenServiceTest`, `security/JwtAuthenticationFilterTest`
- [x] `services/AuthServiceTest`, `services/UserServiceTest`, `services/OrderServiceTest`, `services/TShirtServiceTest`, `services/CustomUserDetailsServiceTest`
- [x] `AppShopRunTest` (`@SpringBootTest`): контекст стартует, бины безопасности на месте, `jwt.*` прочитаны, таблица `refresh_tokens` читается.
- Проверка: `mvn test` → **Tests run: 80, Failures: 0, Errors: 0 — BUILD SUCCESS** ✅

### Этап 7 — Каркас фронтенда
- [x] `frontend/`: `package.json` (React 18, react-router-dom 6, axios, Vite 5, TS 5), `vite.config.ts` (proxy `/api` → `:8080`), `tsconfig.json`, `index.html`, `src/main.tsx`, `src/App.tsx`, стили (`styles/tokens.css`, `layout.css`, `components.css`, `pages.css`).
- [x] Слой API: `src/api/http.ts` (axios + интерсепторы: подстановка Bearer, ротация refresh по shared-promise, повтор запроса, `ApiRequestError`), `src/api/{auth,catalog,orders,admin}.ts`, `src/types/api.ts`.
- [x] Контексты: `context/AuthContext.tsx` (bootstrap сессии через `/auth/me`, `onSessionLost`), `context/CartContext.tsx` (localStorage), `auth/tokenStorage.ts`.
- [x] Роуты: `components/ProtectedRoute.tsx` (в т.ч. `adminOnly`), сохранение «куда шёл» перед логином.
- Проверка: `npm install && npx tsc --noEmit && npm run build` ✅ (124 модуля, dist собран).

### Этап 8 — Страницы фронтенда (полная замена старого UI)
- [x] `CatalogPage` (размер/наличие/сортировка/пагинация — всё в query-параметрах URL), `ProductPage` (галерея, смена активного фото), `LoginPage`, `RegisterPage`, `CartPage`, `CheckoutPage`, `OrdersPage`, `OrderDetailsPage`, `NotFoundPage`.
- [x] Админ: `admin/AdminProductsPage` (список + скрытие товара + «показывать скрытые»), `admin/AdminProductFormPage` (создание/редактирование, multipart-загрузка до 5 картинок, выбор превью, удаление картинки), `admin/AdminOrdersPage` (все заказы + смена статуса), `admin/AdminUsersPage`.
- [x] Компоненты: `Header`, `Layout`, `ProductCard`, `Pagination`, `Spinner`, `ApiErrorBox`, `ProtectedRoute`.
- [x] Ошибки бэкенда показываются из `ApiError.message` + `ApiError.fields` (валидация не дублируется во фронтенде).
- Проверка: `npm run build` ✅

### Этап 9 — Интеграционная проверка (e2e-смоук)
- [x] `scripts/run-backend.sh` / `scripts/stop-backend.sh` (запуск и остановка бэкенда, ожидание `Started AppShopRun`).
- [x] `scripts/smoke-test.sh`: 19 сценариев на реальный HTTP — гость/USER/ADMIN, rotation refresh, отзыв токена при logout, multipart-загрузка картинки, заказ, 401/403/404/409/400. Итог: **ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ** ✅
- [x] Проверен прокси Vite: `curl http://localhost:5173/api/tshirts` → JSON из MySQL; `curl http://localhost:5173/api/auth/me` → 401.
- [x] `REPORT.md` (подробное объяснение), `README.md`, `frontend/README.md`.

### Этап 10 — Чистовая проверка (после всех правок)
- [x] `bash scripts/mvn.sh test` → 80 тестов, BUILD SUCCESS.
- [x] `bash scripts/smoke-test.sh` → все проверки зелёные (после перехода на мягкое удаление и `Long`-id).
- [x] `cd frontend && npm run build` → сборка без ошибок.
- [x] Связка «Vite dev + бэкенд» проверена живым curl.

## 3. Критерии готовности

- `mvn test` — зелено; `mvn test-compile` — зелено.
- `cd frontend && npm run build` — зелено (`tsc` без ошибок).
- API работает без сессий: `Authorization: Bearer <access>`, refresh работает, logout отзывает refresh-токен.
- Старый Thymeleaf-интерфейс выключен, код сохранён в `_legacy_thymeleaf/`.
- Таблицы MySQL не удалялись; новая таблица `refresh_tokens` создана Hibernate (`ddl-auto=update`).
- `REPORT.md` содержит подробное объяснение на русском по каждому этапу.
