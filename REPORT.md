# REPORT.md — переход на REST API + Stateless JWT и React-клиент

Документ написан в формате «что сделано → как это работает → на что смотреть».
Все пути указаны относительно корня проекта `D:\website\ClothesWebsite-master`.

Содержание:

1. [Что было и что стало](#1-что-было-и-что-стало)
2. [Как запустить и как проверить](#2-как-запустить-и-как-проверить)
3. [Хронология работ по этапам](#3-хронология-работ-по-этапам)
4. [Как работает JWT в этом проекте](#4-как-работает-jwt-в-этом-проекте)
5. [Архитектура бэкенда](#5-архитектура-бэкенда)
6. [База данных: что изменилось](#6-база-данных-что-изменилось)
7. [Безопасность: что реально закрыто](#7-безопасность-что-реально-закрыто)
8. [Фронтенд: как устроен и как общается с API](#8-фронтенд-как-устроен-и-как-общается-с-api)
9. [Тесты: 80 автотестов и 19 HTTP-сценариев](#9-тесты-80-автотестов-и-19-http-сценариев)
10. [Найденные баги и как они были исправлены](#10-найденные-баги-и-как-они-были-исправлены)
11. [Слабые места и что изучить дальше](#11-слабые-места-и-что-изучить-дальше)
12. [Справочник файлов и команд](#12-справочник-файлов-и-команд)

---

## 1. Что было и что стало

| Аспект | Было (Thymeleaf-монолит) | Стало |
|---|---|---|
| Интерфейс | Thymeleaf-шаблоны + `static/*.css|js` на том же Spring-приложении | React SPA в `frontend/` (Vite, TS, React Router). Старый UI перенесён в `_legacy_thymeleaf/` |
| Аутентификация | `HttpSession` + форма логина Spring Security, `formLogin()`, редиректы | Stateless JWT: Access (15 мин) + Refresh (7 дней, ротация, хэш в БД) |
| Корзина | `HttpSession` (`Cart` в сессии) | Клиентская корзина (`CartContext` + `localStorage`), на сервер уходит список `tshirtIds` |
| Доступ к товарам | сервер рендерил HTML | JSON-эндпоинты `/api/tshirts` с фильтром по размеру/наличию, сортировкой и пагинацией |
| Сортировка | поле-одиночка в контроллере `currentSize` (состояние в бине!) | белый список `TShirtSort` + `Specification` + `Pageable`; состояния в бине нет |
| Ответы об ошибках | HTML-страницы / дефолтный `/error` | единый JSON `ApiError` (`exceptions/ApiExceptionHandler.java`) |
| Сущности в JSON | `User`, `TShirt`, `Order` отдавались как есть | только DTO-records (`dto/*`), мапперы в `mapper/*` |
| Роли | `USER` / `ADMIN` через сессию | те же роли, но в claims токена и в `SecurityFilterChain` |
| Документация API | её не было | Swagger UI: `/swagger-ui.html` (springdoc) |
| Тесты | 1 «тест», который просто поднимал контекст | 80 тестов (unit + интеграция) и смоук-скрипт на реальный HTTP |

Архитектурные решения принимались по правилам `AGENTS.md`: Spring Security 6 через
`SecurityFilterChain` и лямбды, слои `Controller(DTO) → Service → Repository`, файлы ≤ ~400 строк,
JUnit 5 + Mockito на новую логику авторизации и сервисов, таблицы MySQL не удалялись,
в Git ничего не коммитилось (репозитория `.git` в проекте нет).

---

## 2. Как запустить и как проверить

```bash
# 0) секреты (один раз)
cp secrets.properties.example secrets.properties   # вписать JWT_SECRET и ADMIN_* при желании

# 1) бэкенд :8080
bash scripts/run-backend.sh      # ждёт появления "Started AppShopRun" в target/backend.log
bash scripts/stop-backend.sh

# 2) фронтенд :5173
cd frontend && npm install && npm run dev

# 3) проверки
bash scripts/mvn.sh test         # 80 тестов, BUILD SUCCESS
bash scripts/smoke-test.sh       # 19 HTTP-сценариев, "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ"
cd frontend && npm run build     # tsc + vite build, dist/ готов
```

Результаты на момент завершения работ:

- `mvn test` → `Tests run: 80, Failures: 0, Errors: 0` — **BUILD SUCCESS**;
- `scripts/smoke-test.sh` → **ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ**;
- `npm run build` → `✓ 124 modules transformed`, `dist/` собран;
- живой прокси Vite проверен curl: `http://localhost:5173/api/tshirts` вернул JSON из MySQL,
  `http://localhost:5173/api/auth/me` без токена вернул `401`.

---

## 3. Хронология работ по этапам

### Этап 1 — расчистка и подготовка

- Старые Thymeleaf-шаблоны, статика и JSP-подобные контроллеры перенесены в `_legacy_thymeleaf/`
  (с `README.md` внутри). Это замена Git-истории: репозитория в проекте нет, поэтому код не удалялся,
  а был изолирован, чтобы Spring не сканировал старые `@Controller`.
- Из `pom.xml` убран `spring-boot-starter-thymeleaf`, добавлены: `jjwt-api/impl/jackson` 0.11.5,
  `springdoc-openapi-starter-webmvc-ui`, `spring-boot-starter-validation`,
  `com.mysql:mysql-connector-j` (вместо устаревшего `mysql-java-driver`), `spring-security-test`.
- `application.properties`: настройки `jwt.*`, `app.cors.*`, лимиты multipart,
  `spring.jpa.open-in-view=false`, путь Swagger, `secrets.properties` как внешний файл
  (`spring.config.import=optional:secrets.properties`), опции bootstrap-администратора
  и очистки refresh-токенов.
- Созданы `scripts/env.sh` (локальный JDK + Maven из `~/.m2/wrapper/dists`), `scripts/mvn.sh`.

Зачем `open-in-view=false`: по умолчанию Spring держит сессию Hibernate открытой на время
всего HTTP-запроса, из-за чего ленивые связи «подтягиваются» прямо в шаблоне/сериализаторе.
Это скрытая N+1-проблема и утечка соединения. Мы отдаём DTO, поэтому ленивые подгрузки нам не нужны.

### Этап 2 — JWT-инфраструктура (`security/`)

Добавлены: `JwtTokenProvider`, `JwtAuthenticationFilter`, `TokenHasher`, `TokenType`, `Roles`,
`UserPrincipal`, `RefreshTokenService`, `entities/RefreshToken`, `repositories/RefreshTokenRepository`,
`config/JwtProperties`, `services/RefreshTokenCleanupJob`. Подробный разбор — в разделе 4.

### Этап 3 — Security 6 и конфигурация

`config/SecurityConfig.java` переписан под Security 6:

- `SecurityFilterChain` + лямбда-синтаксис (`http.csrf(csrf -> csrf.disable())`,
  `sessionCreationPolicy(STATELESS)`, `authorizeHttpRequests(auth -> auth...)`);
- `httpBasic` и `formLogin` выключены — вместо них JSON-ответы:
  `RestAuthenticationEntryPoint` (401) и `RestAccessDeniedHandler` (403);
- `JwtAuthenticationFilter` добавлен до `UsernamePasswordAuthenticationFilter`;
- `config/AuthenticationConfig` даёт `BCryptPasswordEncoder`, `DaoAuthenticationProvider`
  и `AuthenticationManager` (логин больше не «магия» автоконфигурации, а явный бин);
- `config/CorsProperties` + `CorsConfigurationSource` — только `http://localhost:5173`
  (переопределяется через `APP_CORS_ALLOWED_ORIGINS`);
- `config/AdminBootstrapRunner` создаёт администратора, если заданы свойства `app.admin.*`
  (в `application.properties` они закомментированы, а `scripts/run-backend.sh` передаёт их
  аргументами `--app.admin.email` / `--app.admin.password` из `ADMIN_EMAIL` / `ADMIN_PASSWORD`)
  и такого пользователя ещё нет; иначе в логе появляется
  «Авто-создание администратора выключено» или «Пользователь … уже существует».
- `.requestMatchers("/error").permitAll()` — важно, см. раздел 10 (без этого все
  внутренние форварды ошибок превращались в 401).

### Этап 4 — DTO, мапперы, сервисы

- 16 DTO-records (`dto/`), 3 маппера (`mapper/`), спецификации `specification/TShirtSpecifications`,
  утилиты `services/TShirtSort` (белый список сортировки) и `services/ImageUploadPolicy`
  (тип/размер картинок), сервисы `AuthService`, `UserService`, `TShirtService`, `ImageService`,
  `OrderService`, обработчик `exceptions/ApiExceptionHandler` + 5 классов исключений.
- Контроллеры принимают и отдают **только** DTO: сущности не сериализуются наружу
  (в старом коде `User` наружу отдавался даже хэш пароля — сейчас `UserDto` без пароля).

### Этап 5 — REST-контроллеры (`rest/`)

`AuthController`, `TShirtController`, `ImageController`, `OrderController`,
`AdminOrderController`, `AdminUserController`. Полная таблица эндпоинтов — в разделе 5.4
и в Swagger UI.

### Этап 6 — тесты бэкенда (по `AGENTS.md`)

10 тестовых классов, 80 тестов. Разбор — в разделе 9.

### Этапы 7–8 — фронтенд (`frontend/`)

Каркас (Vite/TS/Router/Axios), слой API с интерцепторами, контексты авторизации и корзины,
14 страниц (включая 4 админских), компоненты, стили. Разбор — в разделе 8.

### Этап 9–10 — интеграционные проверки

`scripts/run-backend.sh`, `scripts/stop-backend.sh`, `scripts/smoke-test.sh` (19 сценариев),
проверка live-прокси Vite, повторный прогон `mvn test` и `npm run build` после всех правок.

---

## 4. Как работает JWT в этом проекте

### 4.1 Два токена вместо одной сессии

| | Access-токен | Refresh-токен |
|---|---|---|
| Назначение | доказывать серверу при каждом запросе, кто ты | один раз в час-два получать новый Access |
| Срок | `jwt.access-token-ttl` = 15 минут | `jwt.refresh-token-ttl` = 7 дней |
| Где хранится у сервера | нигде (stateless, проверка подписи) | только **SHA-256-хэш** в таблице `refresh_tokens` |
| Где передаётся | заголовок `Authorization: Bearer …` | только в теле `POST /api/auth/refresh` / `/logout` |
| Подпись | HS512 (`jwt.secret`) | HS512 (`jwt.secret`) |

Класс `security/JwtTokenProvider.java` — единственное место, где токен создаётся и разбирается.
Claims: `sub` (e-mail), `uid` (id пользователя), `roles`, `typ` (`access` | `refresh`),
`iat`, `exp`, `iss`.

Ключевой момент безопасности: **refresh-токен не принимается как Access**. Фильтр смотрит claim
`typ` и отказывает, если это refresh (`JwtAuthenticationFilterTest.refreshTokenInHeaderIsRejected`).
Иначе украденный «долгий» токен стал бы универсальным пропуском на каждый запрос.

### 4.2 Как запрос становится «аутентифицированным» (`OncePerRequestFilter`)

`security/JwtAuthenticationFilter.java` выполняется на каждый запрос ровно один раз
(гарантия `OncePerRequestFilter`) и делает 4 шага:

1. достаёт заголовок `Authorization`, требует префикс `Bearer `;
2. зовёт `JwtTokenProvider.parse(...)`: проверяется подпись, срок, издатель, тип токена;
3. если всё хорошо — кладёт в `SecurityContextHolder` объект `UserPrincipal`
   (реализует `UserDetails`, дополнительно хранит `id` и флаг `admin`) с ролями
   `ROLE_USER` (+ `ROLE_ADMIN` для админа);
4. если токен плохой — **не отдаёт ошибку сам**, а пишет причину в атрибут запроса
   `RestAuthenticationEntryPoint.ATTR_JWT_ERROR` и пропускает запрос дальше.

Почему «не отдаёт ошибку сам»: фильтр не знает, публичный это URL или нет. Решение «пускать или
нет» принимает `SecurityFilterChain`. Поэтому публичный `GET /api/tshirts` с просроченным токеном
всё равно отдаст товары, а защищённый `/api/orders/me` → 401 с человеческим текстом:
«Срок access-токена истёк. Обновите сессию через /api/auth/refresh».

Дальше Spring Security сам сопоставляет URL из `SecurityConfig` с ролями:
`hasRole("ADMIN")`, `isAuthenticated()`, `permitAll()`.

### 4.3 Логин

`services/AuthService.login()`:

1. `AuthenticationManager` + `DaoAuthenticationProvider` → `CustomUserDetailsService` грузит
   пользователя, BCrypt сравнивает пароль;
2. при неуспехе всегда один текст: **«Неверный e-mail или пароль»** — чтобы нельзя было
   проверить существование аккаунта по ответу (`AuthServiceTest`, `CustomUserDetailsServiceTest`);
3. при успехе: `JwtTokenProvider.createAccessToken()` + `RefreshTokenService.issue()`
   (в ответ уходит новый refresh, в БД — его SHA-256-хэш, срок, `userId`, `revoked=false`).

### 4.4 Ротация refresh-токена (главное, что стоит изучить)

`security/RefreshTokenService.java` + `POST /api/auth/refresh`:

```
клиент: { "refreshToken": "<старый>" }
сервер:  1) hash = SHA-256(<старый>)          // TokenHasher
         2) ищет запись по hash; проверяет revoked=false и expiresAt > now
         3) помечает СТАРЫЙ отзыв (revoke)    // это и есть ротация
         4) выдаёт новый refresh + новый access
клиент:  кладёт новую пару в localStorage и повторяет исходный запрос
```

Что это даёт на практике (проверено смоуком и `RefreshTokenServiceTest`):

- старый refresh после обновления **не работает** (`401`);
- refresh нельзя использовать вместо access;
- `POST /api/auth/logout` отзывает refresh → сразу после этого refresh возвращает `401`;
- при падении/закрытии браузера запись всё равно протухает по `expiresAt`, а
  `services/RefreshTokenCleanupJob` (`@Scheduled`, `app.refresh-token.cleanup-*`) чистит мусор.

Хэш вместо самого токена: если злоумышленник получит дамп таблицы `refresh_tokens`, он не сможет
ими воспользоваться — SHA-256 необратим, а сам токен не восстанавливается из хэша.

### 4.5 401 vs 403 и единый формат ошибки

- `RestAuthenticationEntryPoint` — 401 «не представился» (нет токена / просрочен);
- `RestAccessDeniedHandler` — 403 «представился, но нельзя» (user лезет в `/api/admin/**`);
- оба возвращают `ApiError` JSON, а не HTML форму логина;
- `exceptions/ApiExceptionHandler` (`@RestControllerAdvice`) собирает в один формат:
  `Bean Validation` → 400 + `fields: {field: message}`, `NotFoundException` → 404,
  `ConflictException` → 409 (дубликат e-mail), `InvalidCredentialsException`/`InvalidTokenException` → 401,
  `AccessDeniedException` → 403, `MaxUploadSizeExceededException` → 413.

Фронтенд печатает `message` и `fields` (`components/ApiErrorBox.tsx`), поэтому правила валидации
живут в одном месте — в DTO на бэкенде.

---

## 5. Архитектура бэкенда

### 5.1 Слои и пакеты

```
rest/        @RestController: только DTO на вход/выход, никаких сущностей, никакой бизнес-логики
services/    бизнес-правила, транзакции (@Transactional), валидация бизнес-смысла
mapper/      сущность → DTO (и обратно для форм)
repositories/ Spring Data JPA (JpaRepository + JpaSpecificationExecutor)
entities/    JPA-сущности (только внутри сервера)
dto/         records-DTO + ApiError + PageResponse
security/    JWT: провайдер, фильтр, principal, refresh-механика, хэширование
config/      SecurityConfig, AuthenticationConfig, Cors, Jwt/AdminProperties, OpenApi
specification/ Criteria-предикаты для фильтров каталога
exceptions/  ApiException + ApiExceptionHandler
```

Правило «контроллер отдаёт только DTO» дало два приятных эффекта: (1) ленивые связи не
вылезают в JSON (`LazyInitializationException` больше не возможен), (2) поле `password`
физически не попадает в ответ.

### 5.2 Каталог: фильтры, сортировка, пагинация

`GET /api/tshirts?size=M&available=true&sort=PRICE_DESC&page=0&pageSize=12`

- `specification/TShirtSpecifications` собирает `Specification<TShirt>` из непустых параметров —
  один запрос к БД вместо набора `if` в разных методах репозитория;
- `services/TShirtSort` — перечисление с белым списком (`NEWEST`, `OLDEST`, `PRICE_ASC`, `PRICE_DESC`,
  `NAME_ASC`); неизвестное значение = `NEWEST`. Клиент не может попросить сортировку по
  произвольной колонке (это и дыра, и способ уронить БД);
- `services/TShirtService.clampPageSize` ограничивает `pageSize` (макс. 50), чтобы запрос
  `?pageSize=1000000` не вытаскивал всю таблицу;
- `dto/PageResponse` — плоский JSON `{content, page, size, totalElements, totalPages, last}`,
  фронтенд по нему строит пагинацию;
- флаг `includeDisabled=true` учитывается **только если токен админский**
  (`principal.isAdmin()` в контроллере), иначе скрытые товары не видны.

### 5.3 Товары, картинки, мягкое удаление

- `POST|PUT /api/tshirts` принимают `multipart/form-data`: часть `data` — JSON
  (`@RequestPart("data") TShirtFormRequest`), часть `files` — картинки (до 5, каждая до 5 МБ).
  Так валидация DTO работает так же, как для обычного JSON;
- `services/ImageUploadPolicy` проверяет количество файлов (≤ 5), размер (≤ 5 МБ), MIME из
  белого списка и **реальное содержимое**: `looksLikeImage` сравнивает «магические числа»
  (PNG/JPEG/GIF/RIFF+WEBP), поэтому `shell.exe` с MIME `image/png` не пройдёт;
- байты картинок лежат в таблице `images.bytes` (`@Lob byte[]`, колонка `longblob`), отдают их
  `ImageController` + `ImageService`. Чтобы не долбить БД при каждой отрисовке превью,
  `ImageController` ставит `Cache-Control: max-age=7 days, public` — повторные показы
  браузер берёт из кэша. В `<img src>` URL публичный —
  токен браузер к `<img>` не подмешивает;
- после загрузки картинки явно сохраняются и делают `flush()`, чтобы получить id до того,
  как выбирается превью и формируется ответ (иначе `previewImageId` был бы `null`);
- `DELETE /api/tshirts/{id}` — **мягкое** удаление: `disabled=true` + `available=false`.
  Жёсткий `DELETE` разбивается о внешний ключ `order_tshirts`: товар уже участвует в заказах,
  историю заказов ломать нельзя. Скрытый товар исчезает из каталога и из карточки (`404`),
  но остаётся в заказах.

### 5.4 Эндпоинты (проверено `scripts/smoke-test.sh`)

| Метод, URL | Кто | Примечание |
|---|---|---|
| `POST /api/auth/register` | все | 201 + пара токенов; дубликат e-mail → 409 |
| `POST /api/auth/login` | все | 200 + пара токенов; неверные данные → 401 с одинаковым текстом |
| `POST /api/auth/refresh` | владелец refresh | ротация: старый отзывается |
| `POST /api/auth/logout` | владелец refresh | 204, refresh отозван |
| `GET /api/auth/me` | авторизованные | профиль из токена |
| `GET /api/tshirts` | все | фильтры/сортировка/пагинация |
| `GET /api/tshirts/sizes` | все | список размеров для фильтра |
| `GET /api/tshirts/{id}` | все | карточка + галерея; скрытый/несуществующий → 404 |
| `POST /api/tshirts` | ADMIN | 201, multipart |
| `PUT /api/tshirts/{id}` | ADMIN | частичная загрузка картинок |
| `PATCH /api/tshirts/{id}/preview/{imageId}` | ADMIN | смена превью |
| `DELETE /api/tshirts/{id}` | ADMIN | 204, мягкое удаление |
| `GET /api/images/{id}` | все | бинарник + `Content-Type` |
| `DELETE /api/images/{id}` | ADMIN | картинка + переназначение превью |
| `POST /api/orders` | авторизованные | 201; пользователь берётся из токена |
| `GET /api/orders/me` | авторизованные | пагинация |
| `GET /api/orders/{id}` | авторизованные | свой заказ; чужой → 403 |
| `GET /api/admin/orders` | ADMIN | все заказы |
| `PATCH /api/admin/orders/{id}/status` | ADMIN | `{ "status": "DELIVERED" }` |
| `GET /api/admin/orders/statuses` | ADMIN | значения enum для селекта |
| `GET /api/admin/users` | ADMIN | список пользователей |

### 5.5 Заказ: как считается итог

`services/OrderService.create()`:

- пользователь **берётся из токена** (`UserPrincipal`), а не из тела запроса — нельзя «оформить
  на другого»;
- позиции: список `tshirts` (связь M—N через `order_tshirts`), но `OrderService` сначала
  схлопывает `tshirtIds` в `LinkedHashSet`, поэтому одна позиция на товар; отсутствующие или
  снятые с продажи товары → `404`/`400`;
- цены берутся из БД на момент оформления, поэтому «подправить цену» во фронтенде нельзя;
- статус `PROCESSING` по умолчанию, меняет его только админ.

---

## 6. База данных: что изменилось

Таблицы **не удалялись**. `spring.jpa.hibernate.ddl-auto=update` только добавил новую таблицу
и колонки:

- `refresh_tokens` — новая (`token_hash` unique, `user_id`, `expires_at`, `revoked`, `issued_at`);
- `tshirts.disabled` — использована существующая колонка для мягкого удаления;
- из `User` убрано дублирующее поле `order` (связь оставлена одна: `Order.user`),
  но **колонка `users.order_id` в MySQL осталась** — я не удаляю колонки и таблицы
  без явного разрешения, это можно сделать отдельной миграцией, когда проверите, что данные там не нужны;
- первичные ключи в сущностях переведены с `int` на `Long` (см. раздел 10), колонки в MySQL
  остались `int` — Hibernate переносит тип корректно, новых миграций не требовалось.

Связи: `users 1—N refresh_tokens`, `users 1—N orders`, `tshirts M—N orders` (`order_tshirts`),
`tshirts 1—N images`. Из `User` убрано дублирующее поле `order`: в старом коде одна и та же связь
была описана дважды (и `User.order`, и `Order.user`) — осталась одна, `Order.user`.

---

## 7. Безопасность: что реально закрыто

1. **Сессий нет**: `SessionCreationPolicy.STATELESS`, CSRF выключен (он нужен для cookie-сессий,
   а токен в заголовке подделать через CSRF нельзя).
2. **Подпись HS512**, секрет ≥ 64 символов; `JwtProperties` отказывает при коротком секрете.
3. **Access ≠ Refresh**: проверка claim `typ`.
4. **Refresh одноразовый** (ротация) + хранится хэш, а не токен.
5. **Logout отзывает** refresh — украденный токен перестает работать.
6. **BCrypt** (`BCryptPasswordEncoder`) — пароль не хранится и не логируется.
7. **Одинаковый текст** при «нет такого e-mail» и «неверный пароль».
8. **Защита от перебора id**: `GET /api/orders/{id}` проверяет владельца (`403` на чужой заказ).
9. **CORS** — конкретный origin + разрешённые заголовки/методы, а не `*`.
10. **Загрузка файлов**: MIME + реальная сигнатура байтов + лимит 5 файлов × 5 МБ + лимиты
    сервера (`max-file-size`/`max-request-size`).
11. **Swagger** доступен, но в проде его стоит закрыть паролем или отключить
    (`springdoc.api-docs.enabled=false`).

Чего сознательно **нет** (и это стоит знать): нет rate-limit на `/api/auth/login`, нет blacklist
для отозванных access-токенов (access живёт 15 минут), нет 2FA, нет.refresh-кражи-детекта
(если украденный refresh используют одновременно с владельцем, вторая попытка даст 401, но
цепочка «кто украл» не анализируется).

---

## 8. Фронтенд: как устроен и как общается с API

### 8.1 Маршруты

```
/catalog, /product/:id, /login, /register, /cart          — публичные
/checkout, /orders, /orders/:id                            — <ProtectedRoute>
/admin/products, /admin/products/edit/:id,
/admin/orders, /admin/users                                — <ProtectedRoute adminOnly>
*                                                          — NotFound
```

`ProtectedRoute` — UX (не пустой экран вместо серии 401), настоящая проверка — на сервере.
Перед логином запоминаем, куда шёл пользователь (`location.state.from`), и возвращаем его туда.

### 8.2 Интерцепторы axios (`src/api/http.ts`) — сердце фронтенда

```
request:  если есть access-токен -> config.headers.Authorization = "Bearer <token>"
response: 401 и это не /auth/* и запрос ещё не повторяли
          -> refreshOnce()  (один общий Promise на все параллельные 401)
          -> сохранить новую пару токенов
          -> повторить исходный запрос с новым access
          -> если refresh не удался: clearTokens() + onSessionLost() -> /login
```

Два важных уточнения для понимания:

- **shared promise** (`refreshInFlight`): если 5 запросов упали с 401 одновременно, refresh
  выполняется один раз, а не пять. Иначе ротация «съела бы» только что выданный refresh
  и часть запросов отвалилась бы;
- refresh идёт через **отдельный axios-инстанс без интерцепторов**, иначе 401 на сам
  `/auth/refresh` вызвал бы рекурсию.

### 8.3 Восстановление сессии при открытии страницы

`AuthContext` на монтировании смотрит: есть ли refresh в `localStorage` → просим `GET /api/auth/me`.
Если access уже протух — интерцептор сам обновит пару и повторит запрос, поэтому пользователь
остаётся залогиненным после перезагрузки/повторного открытия браузера.

### 8.4 Корзина

`CartContext` хранит `[{id, name, size, price, previewImageId}]` в `localStorage`.
Количеств нет: позиция = товар (сервер схлопывает дубликаты `tshirtIds` в `LinkedHashSet`),
поэтому дубликаты отсекаются и здесь,
и в `OrderService`. Итог «для глазами» считается во фронтенде, финальный — на сервере по ценам из БД.

### 8.5 Catalog и фильтры

Фильтры живут в query-параметрах URL (`?size=M&sort=PRICE_DESC&page=1`): ссылку можно сохранить
и отправить, кнопка «назад» работает как в обычном сайте. При смене фильтра страница
сбрасывается на первую, параллельные ответы защищаются флагом `alive` (результат устаревшего
запроса не перетирает новый).

### 8.6 Картинки

Везде `<img src="/api/images/{id}">`: эндпоинт публичный, токен не нужен (браузер его к `<img>`
не приложит). Метаданные галереи приходят из `TShirtDetailsDto.images`, там же флаг `previewImage`.

### 8.7 Обработка ошибок и загрузки

`ApiRequestError` (наш класс) несёт `status`, `message`, `fields` — `ApiErrorBox` рисует общее
сообщение и полевые подсказки. Экраны показывают `Spinner` на время запроса; при недоступном
бэкенде сообщение «Сервер недоступен. Проверьте, запущен ли бэкенд».

### 8.8 Стили

Вместо россыпи старых файлов (`basket.css`, `delete.css`, `cartlayout.css` и т.д. из
`_legacy_thymeleaf/static/`) — 3 файла: `tokens.css` (CSS-переменные и сброс), `layout.css`
(шапка, сетка, каркас), `components.css` (кнопки, поля форм, карточки, бейджи, алерты) и
`pages.css` (таблицы админки, корзина, оформление, карточка товара, галерея, пагинация,
спиннер) — каждый файл до 400 строк, как требует `AGENTS.md`.
Визуальный стиль стал нейтральным «магазином»: карточки с превью 3:4, бейджи наличия и статусов
заказа, адаптив через `grid-template-columns: repeat(auto-fill, minmax(...))`.

---

## 9. Тесты: 80 автотестов и 19 HTTP-сценариев

### 9.1 unit (JUnit 5 + Mockito, без БД)

| Тест | Что доказывает |
|---|---|
| `JwtTokenProviderTest` (8) | access/refresh имеют разный TTL и claim `typ`; просроченный невалиден; чужая подпись отклонена; claims содержат `uid`, e-mail, роли |
| `TokenHasherTest` (4) | SHA-256 детерминирован, различает похожие токены, длина 64 hex |
| `RefreshTokenServiceTest` (10) | выпуск/проверка, отзыв, **ротация** (старый перестаёт действовать), отозванный/истёкший → отказ, очистка устаревших |
| `JwtAuthenticationFilterTest` (6) | Bearer access → аутентификация с ролями; refresh в заголовке → отказ; просроченный → подсказка про `/api/auth/refresh`; поддельная подпись → анонимность; `Basic …` не принимается; без заголовка запрос идёт дальше |
| `AuthServiceTest` (8) | регистрация: 409 на дубликат e-mail, роль USER по умолчанию; логин: нормальные данные дают пару токенов, неверные — 401 с одним и тем же текстом |
| `UserServiceTest` (8) | поиск по e-mail/id, 404 на отсутствие, `UserDto` без пароля, админ-наследование ролей |
| `OrderServiceTest` (13) | заказ привязан к пользователю из токена; дубликаты позиций схлопываются; адрес обрезается/нормализуется; `pageSize` ограничен; свои заказы видит только владелец; админ может любой заказ; смена статуса |
| `TShirtServiceTest` (16) | сортировка по белому списку и защита от `price; drop table…`; `pageSize`/`page`; скрытый товар не отдаётся; создание назначает превью и id картинок; нормализация имени/размера; >5 файлов / >5 МБ / `shell.exe` / файл-подделка «image/png» с EXE-байтами → 400; превью сохраняется при правке; мягкое удаление без `delete()`; картинка из другого товара недоступна |
| `CustomUserDetailsServiceTest` (3) | id/роли попадают в `UserDetails`; у админа 2 роли; неизвестный пользователь → тот же текст ошибки |

### 9.2 интеграция

`AppShopRunTest` (`@SpringBootTest`): контекст стартует, бины безопасности в единственном числе
(ловит конфликты имён вроде старого `SecurityConfig`), `jwt.*` прочитаны из конфига,
таблица `refresh_tokens` читается.

### 9.3 Смоук на реальный HTTP (`scripts/smoke-test.sh`)

19 групп проверок: гость видит каталог; гость не видит `/api/orders/me` (401); регистрация (201);
дубликат e-mail (409); неверный пароль (401); кривой e-mail (400); `/me` по access;
refresh даёт новую пару; старый refresh после ротации (401); refresh как access (401);
user не создаёт товар (403); логин админа; multipart-создание товара с настоящей картинкой (201);
поддельная картинка (EXE-байты с MIME `image/png`) → 400; гость видит карточку и картинку (200);
заказ (201); заказ несуществующего товара (404); пустая корзина (400); свои заказы (200) и
чужие/админские (403); админ видит все заказы и меняет статус (200); logout (204) и
последующий refresh (401); мягкое удаление (204 → 404 → 404).

---

## 10. Найденные баги и как они были исправлены

Это самые полезные места для самостоятельного чтения.

1. **`Could not create query for ImageRepository.countByTShirtId`** — Spring Data не смог
   разобрать `TShirtId` для поля `Image.tShirt`. Решение: явный JPQL в
   `repositories/ImageRepository.java` (`findAllByTShirt`, `countByTShirt` с `@Param`).
   Учить: у производных имён методов есть пределы применимости; при вложенных связях надёжен JPQL.
2. **`ClassCastException: Long cannot be cast to Integer`** при создании заказа — репозитории
   объявлены как `JpaRepository<Entity, Long>`, а в сущностях поле id было `int`.
   Решение: id в `TShirt`, `Image`, `Order`, `User` → `Long`; `TShirt.previewImageId` остался `int`
   (колонка в MySQL `int`), для него используется `Math.toIntExact(...)`.
3. **`previewImageId` был `null` в ответе** — id картинок ещё не были присвоены.
   Решение: в `TShirtService.attachImages` явные `saveAll(...)` + `flush()` до выбора превью.
4. **Все ошибки превращались в 401** — Spring форвардит обработчик ошибок на `/error`,
   а `/error` не был разрешён в `SecurityFilterChain`. Решение: `.requestMatchers("/error").permitAll()`.
5. **`DELETE /api/tshirts/{id}` → 409/500** из-за внешнего ключа `order_tshirts`.
   Решение: мягкое удаление (`disabled` + `available=false`) и фильтрация скрытых в `getOrThrow`.
6. **Заказ возвращал 500, а не понятную ошибку** — не хватало проверок «товара нет»/«корзина пуста».
   Решение: `NotFoundException`/`InvalidRequestException` в `OrderService`, в смоуке зафиксировано `404`/`400`.
7. **Некорректные HTTP-статусы**: создание товара возвращало 200 → добавлен
   `@ResponseStatus(HttpStatus.CREATED)`; удаление возвращало JSON-«ок» → `ResponseEntity<Void>` + `204`.
8. **UTF-8 в смоук-тесте (Windows)**: curl из Git Bash отправлял кириллицу байтами, которые
   Jackson не мог прочитать (`Invalid UTF-8 middle byte 0xe5`). В смоук-тестах payloads ASCII;
   в реальном UI проблема не возникает (axios всегда шлёт корректный UTF-8 JSON).
   Учить: кодировки в цепочке «терминал → curl → сервер» надо контролировать явно.
9. **Временные файлы в `/tmp`** ломались у Node на Windows — временные каталоги смоука
   перенесены в `target/smoke-*`, пути для curl конвертируются `cygpath -w`.
10. **Состояние в контроллере** (старая ошибка: `currentSize` в бине-синглтоне) — из-за этого
    фильтры «перетекали» между запросами разных пользователей. Решение: фильтры полностью
    в параметрах запроса + `Specification`; во фронтенде — query-параметры URL.

---

## 11. Слабые места и что изучить дальше

Что стоит открыть и прочитать в первую очередь:

1. `security/JwtAuthenticationFilter.java` + `config/SecurityConfig.java` — как запрос
   проходит путь «заголовок → `SecurityContextHolder` → проверка прав». Это ядро миграции.
2. `security/RefreshTokenService.java` + `security/TokenHasher.java` — ротация и хранение хэша;
   сравните с `JwtAuthenticationFilterTest` и `RefreshTokenServiceTest`, чтобы понять,
   какие именно атаки это закрывает.
3. `frontend/src/api/http.ts` — интерцепторы и shared-promise refresh; без понимания этой схемы
   непонятно, почему пользователь «не замечает» истечения access-токена.
4. `specification/TShirtSpecifications.java` + `services/TShirtSort.java` — как строится
   безопасная фильтрация/сортировка вместо «ручных» if в контроллере.

Что не сделано и почему (осознанные упрощения):

- **Токены в `localStorage`** (а не в `httpOnly`-cookie): любой XSS их читает.
  Усиление: refresh в `httpOnly`+`SameSite=Lax` cookie, access только в памяти JS,
  cookie-based `/api/auth/refresh` + CSRF-подтверждение. Фронтенд это переживёт спокойно —
  менять нужно только `auth/tokenStorage.ts` и `api/http.ts`.
- **Нет отзыва access-токенов**: access живёт 15 минут и отозвать его нельзя.
  Усиление: более короткий TTL + blacklist в Redis по `jti`, либо `token_version` в таблице `users`.
- **Нет rate-limit и блокировок** на `/api/auth/login` (подбор пароля не тормозится).
  Усиление: `Bucket4j`/фильтр + `AUTH_FAILURE` счётчик, CAPTCHA.
- **Картинки в БД (`images.bytes longblob`)**: каждое попадание мимо браузерного кэша читает BLOB из MySQL.
  Усиление: файловое хранилище/S3, в БД — путь и content-type, плюс `ETag`/CDN.
- **Количества товара нет** (Set вместо List): если понадобится «2 штуки одного размера»,
  придётся менять `Order.tshirts` на промежуточную сущность `OrderItem` с колонкой `quantity`
  и чинить схему БД (это уже миграция, а не `ddl-auto=update`).
- **Нет редактирования профиля и смены пароля**, нет писем для восстановления пароля.
- **Тесты контроллеров не через MockMvc**: проверены фильтр+сервисы+контекст. Следующий шаг —
  `MockMvcTester`/`@WebMvcTest` со `@WithMockUser` для проверки связки URL↔роли в `SecurityConfig`
  (сейчас права подтверждает только смоук-тест на живой HTTP).
- **Нет миграций (Flyway/Liquibase)**: схема «чинится» `ddl-auto=update`, что нормально для учёбы
  и опасно для прода. Следующий шаг: `flyway-core` + `db/migration/V1__baseline.sql`.
- **Логирование и мониторинг**: только `System.out`-лог Spring. Стоит добавить `logback`-файл,
  `traceId` в MDC и метрики Actuator (и закрыть `/actuator/**`).
- **Frontend-роуты не ленивые** (`React.lazy` убрал из плана ради простоты): при росте числа
  страниц стоит разделить бандл (сейчас 260 КБ / 85 КБ gzip).

---

## 12. Справочник файлов и команд

### Бэкенд (ключевые файлы)

```
src/main/java/com/example/appShop/
  config/       SecurityConfig, AuthenticationConfig, CorsProperties, JwtProperties,
                AdminBootstrapProperties + AdminBootstrapRunner, OpenApiConfig
  security/     JwtTokenProvider, JwtAuthenticationFilter, RefreshTokenService, TokenHasher,
                UserPrincipal, TokenType, Roles, RestAuthenticationEntryPoint, RestAccessDeniedHandler
  services/     AuthService, UserService, TShirtService, ImageService, OrderService,
                CustomUserDetailsService, TShirtSort, ImageUploadPolicy, RefreshTokenCleanupJob
  rest/         AuthController, TShirtController, ImageController, OrderController,
                AdminOrderController, AdminUserController
  dto/          16 records (+ ApiError, PageResponse)
  mapper/       UserMapper, TShirtMapper, OrderMapper
  repositories/ UserRepository, TShirtRepository, ImageRepository, OrderRepository,
                RefreshTokenRepository
  entities/     User, TShirt, Image, Order, RefreshToken
  specification/TShirtSpecifications
  exceptions/   ApiException + NotFound/Conflict/InvalidRequest/InvalidCredentials/InvalidToken
                + ApiExceptionHandler
src/main/resources/application.properties
src/test/java/com/example/appShop/…            10 тестовых классов, 80 тестов
```

### Конфигурация и скрипты

```
secrets.properties.example / secrets.properties   JWT_SECRET, ADMIN_EMAIL, ADMIN_PASSWORD
scripts/env.sh            локальный JDK + Maven (в PATH их нет)
scripts/mvn.sh            mvn с нужным JAVA_HOME
scripts/run-backend.sh    запуск бэкенда + ожидание "Started AppShopRun" (лог target/backend.log)
scripts/stop-backend.sh   остановка по порту 8080 + снятие PID из target/backend-mvn.pid
scripts/smoke-test.sh     19 HTTP-сценариев (curl + node для разбора JSON)
_legacy_thymeleaf/        старый UI и старые @Controller (сохранены, не участвуют в сборке)
```

### Чек-лист «свежий разработчик»

```bash
cp secrets.properties.example secrets.properties
bash scripts/run-backend.sh
curl -s localhost:8080/api/tshirts | head -c 200
curl -s -X POST localhost:8080/api/auth/login \
     -H 'Content-Type: application/json' \
     -d '{"email":"admin@clothes.shop","password":"SuperSecret123"}' | head -c 200
cd frontend && npm install && npm run dev      # http://localhost:5173
bash scripts/stop-backend.sh                    # когда закончили
```

### Что где смотреть в браузере

- Swagger: `http://localhost:8080/swagger-ui.html` (можно «погонять» API руками);
- Фронтенд: `http://localhost:5173` (админ — `admin@clothes.shop` / `SuperSecret123`,
  если вы включали bootstrap-администратора);
- DevTools → Network: видно, как после 401 уходит `/api/auth/refresh` и повторяется исходный запрос.
