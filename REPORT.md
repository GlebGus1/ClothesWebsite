# Отчёт: доработка интернет-магазина ClothesShop

Дата: 25.11.2026
Ветка/рабочая копия: `D:\website\ClothesWebsite-master` (ветка `react-jwt-migration`; изменения **не закоммичены** — по правилу проекта коммит только с вашего разрешения).

В этой итерации шесть блоков: (1) безопасная работа с отменёнными заказами + фильтр статусов в админке, (2) коллекции (дропы) с бейджами на карточках и баннером в каталоге, (3) аналитика: просмотры товаров, воронка продаж, отчёт по промокодам, дашборд, (4) cookie-баннер и обезличенный трекинг, (5) блокировка (бан) пользователей и отзыв сессий, (6) защита от ботов: лимиты запросов по IP + капча Cloudflare Turnstile. Плюс мелкие правки вёрстки/адаптивности и два юзабилити-апгрейда админки (быстрое «в наличии», карточки товаров в заказах).

---

## 0. Как проверить результат

```bash
# бэкенд: компиляция + все юнит/срезовые тесты (без MySQL)
bash scripts/mvn.sh -o -q test-compile
bash scripts/mvn.sh -o test -Dtest='!AppShopRunTest' -DfailIfNoTests=false

# бэкенд: полный старт контекста на реальной MySQL (см. раздел «База данных»)
bash scripts/mvn.sh -o test -Dtest=AppShopRunTest -DfailIfNoTests=false

# фронтенд: строгая типизация (tsc --noEmit) + production-сборка
cd frontend && npm run build
```

Результаты прогона — в разделе «Проверки» ниже. `AppShopRunTest` в этот раз запускался: MySQL была запущена, и именно он применил новые таблицы/колонки в локальной БД `appshop` (только `create table` / `alter table add column`, ничего не удалялось).

---

## 1. Отменённые заказы: фильтр статусов и честный возврат товаров в продажу

### Проблема
Раньше список заказов в админке показывал всё подряд, а смена статуса на «отменён» автоматически снова выставляла товары «в наличии». Это расходится с реальностью: отменённый заказ физически может быть уже в доставке, и автоматический возврат в продажу приводил к двойной продаже одной и той же футболки.

### Что сделано в бэкенде
- `services/OrderService.java`
  - `search(...)` фильтрует по статусу **на уровне БД** (`findAllByOrderStatusOrderByIdDesc`), а не «в памяти после выборки» — список админа не растёт вместе с историей магазина;
  - `updateStatus(...)` **больше не трогает** `available` у товаров;
  - добавлен явный `restoreItemsToSale(orderId)`:
    - работает **только** для `OrderStatus.CANCELED`;
    - пропускает товары, уже проданные в других не-отменённых заказах (`OrderRepository.findProductIdsSoldInOtherOrders`);
    - пропускает снятые с продажи (`is_disabled`);
    - возвращает `RestoreItemsDto(order, restoredCount, skippedNames)` — в `skippedNames` лежит причина по каждой позиции, например `«Футболка 2 (уже продан в другом заказе)»`.
- `rest/AdminOrderController.java`:
  - `GET  /api/admin/orders?status=CANCELED&page=0&pageSize=20`
  - `PATCH /api/admin/orders/{id}/status`
  - `POST /api/admin/orders/{id}/restore-items`
  - `GET  /api/admin/orders/statuses` — фронтенд берёт список статусов **откуда должен**, а не хардкодит enum.

### Что сделано во фронтенде
- `pages/admin/AdminOrdersPage.tsx`: чипсы статусов (`ALL / PROCESSING / IN_TRANSIT / DELIVERED / CANCELED`), выбранный статус живёт в URL (`?status=CANCELED` → ссылка «все отменённые» шерится и работает кнопка «назад»), у отменённого заказа появляется кнопка **«вернуть товары в продажу»** с подтверждением и понятным фидбеком (`вернуто N; пропущено: …`).
- новый `components/OrderItemsList.tsx` — вместо «голого» текста с названиями карточки позиций: превью, имя, размер, цена. `variant='admin'` ведёт на `/admin/products/edit/{id}`, `variant='customer'` — на страницу товара; та же компонента теперь в `/orders/{id}` клиента.

### Почему именно так
Отмена — это финансовое и складское событие, а не технический флаг. Разделив «пометить заказ отменённым» и «вернуть товар на полку», мы получили аудиторию решений: админ видит, что именно и почему не вернулось, а автоматика ничего не портит.

---

## 2. Коллекции (дропы)

### Модель
- новая сущность `entities/Collection.java` (таблица `collections`): `name` (уникальный), `description`, `badgeColor` (HEX), `active`, `createdAt`;
- у `TShirt` добавлена опциональная связь `collection` (`FetchType.EAGER` — иначе каталог с бейджами соберёт N+1 запросов);
- удаление коллекции **не удаляет товары**: `TShirtRepository.clearCollection(id)` отвязывает их (`CollectionServiceTest` это фиксирует).

### API
- публично: `GET /api/collections` — **только активные** полки (параметра `?all=true` больше нет: публичный контроллер не должен ветвиться по роли, полный список — админский путь);
- фильтры каталога: `GET /api/tshirts?collectionId=3`;
- админ (`/api/admin/collections`, роль ADMIN): `GET`, `POST`, `PUT /{id}`, `DELETE /{id}`.

### UI
- витрина: `components/CollectionsStrip.tsx` — компактный баннер выбранной коллекции + чипсы-переключатели с количеством товаров; цвет акцента приезжает с бэкенда как CSS-переменная `--accent`;
- карточка товара: бейдж дропа и акцентная рамка (`.card--collection`, `.card__drop`);
- страница товара: название коллекции + счётчик просмотров;
- админка: `pages/admin/AdminCollectionsPage.tsx` + `CollectionFormPanel.tsx` (название, описание, пипетка цвета, флаг «активна», счётчик товаров), в таблице товаров — фильтр по коллекции (`?collection=3`) и выбор полки в форме товара.

### Валидация
Имя нормализуется (trim + регистронезависимая проверка уникальности), цвет приводится к `#RRGGBB` в верхнем регистре (`ff6b6b` → `#FF6B6B`), поэтому инлайн-переменная `--accent` безопасна.

---

## 3. Аналитика: просмотры, воронка, промокоды, дашборд

### Счётчик просмотров товара
- `TShirt.viewsCount` + атомарный `TShirtRepository.increaseViewsCount(id)` (`@Modifying`) — без read-modify-write, иначе параллельные просмотры «съедают» счётчик;
- учёт ведёт **только** публичный `GET /api/tshirts/{id}` при наличии заголовка `X-Visitor-Id`. Отдельной ручки «записать просмотр» намеренно нет: две точки учёта = двойные просмотры (это прямо закомментировано в `rest/TrackingController.java`);
- анти-спам на уровне БД: в `tracking_events` уникален `dedupe_key` (`view:2026-11-25:12:abcd…`), то есть один посетитель + один товар + один день = один просмотр, и гарантирует это само ограничение БД даже при гонке двух вкладок (`RepositoryQueryTest` проверяет на реальном H2).

### Воронка продаж
`entities/TrackingEvent` (таблица `tracking_events`) хранит `SITE_VISIT`, `PRODUCT_VIEW`, `CART_ADD` с обезличенным ключом посетителя. События шлёт фронтенд **только после согласия в cookie-баннере**:
- `POST /api/track/visit` — раз за сессию браузера;
- `POST /api/track/cart-add` — только при реальном добавлении (не при повторном клике по уже лежащему в корзине товару);
- просмотры — см. выше.

`services/AnalyticsService.buildFunnel(...)` считает конверсии дважды: от предыдущего шага и от визитов; заказы берутся из БД (живые заказы), а не из событий.

### Дашборд и отчёты
- `GET /api/admin/analytics/dashboard` — выручка, сумма скидок, суммы до скидок, число заказов/отменённых/товаров/клиентов, воронка, топ-5 по просмотрам, топ-5 по «избранному»;
- `GET /api/admin/analytics/promo-usage` — по каждому коду: сколько раз использован, суммы до/после, «упущенная выгода» и список заказов (с EMAIL, датой и **статусом**);
- отменённые заказы не участвуют ни в выручке, ни в скидках, ни в отчёте промокодов (`AnalyticsServiceTest` фиксирует правило);
- отчёт построен на «слепках» (`Order.promoCode`, `Order.discountPercent`), поэтому переименование или удаление промокода не переписывает историю.

### UI
`pages/admin/AdminAnalyticsPage.tsx` + `components/analytics/{StatCards,FunnelPanel,TopProductsPanel,PromoUsagePanel}.tsx`. Числа не пересчитываются в браузере — только форматирование. Если конверсия шага > 100%, панель честно поясняет причину: часть покупателей не приняла cookie-согласие, их событий нет, а заказы считаются по БД.

### Приватность
- в БД не лежат IP, e-mail и «отпечатки» браузера: только обезличенный ключ (`VisitorKeyFactory`), salt процессный, IP в ключ не попадает;
- ключ посетителя живёт в `localStorage` фронтенда (`utils/visitorId.ts`), бэкенд умеет и «без ключа» (тогда фолбэк по хешу IP, тоже без хранения самого IP).

---

## 4. Cookie-баннер

- `components/CookieConsentBanner.tsx` монтируется в `Layout.tsx` (один раз на приложение, а не на страницу);
- решение («принять»/«отклонить») хранится в `localStorage` (`utils/consent.ts`), баннер больше не показывается;
- при «отклонить» фронтенд **не шлёт** ни `/api/track/**`, ни заголовок `X-Visitor-Id` — трекинг просто не происходит (`api/tracking.ts`, `visitorHeaders()`);
- сервер согласие не проверяет (он не может увидеть `localStorage`) и не хранит персональные данные — лимиты против спама он всё равно применяет.

---

## 5. Блокировка пользователей (бан / чёрный список)

- `User.banned` (колонка `users.banned`, `default false`);
- `GET /api/admin/users` — список со статусами; `PATCH /api/admin/users/{id}/ban` телом `{"banned": true|false}`;
- `services/UserAdminService` при блокировке **отзывает все refresh-токены** пользователя (сессия не может продлеваться; доступный access-токен «доживает» максимум 15 минут — это осознанное ограничение stateless-архитектуры без мгновенной инвалидации);
- `security/AccountStatusGuard`: вход и refresh проверяют бан и возвращают `403 Forbidden` с текстом `Аккаунт заблокирован. Обратитесь к администратору сервиса.`;
- админ **не может заблокировать сам себя** (`ConflictException` — иначе легко закрыть себе доступ в админку);
- `JwtAuthenticationFilter` не бросает исключение внутри фильтра (там его не перехватит обработчик Spring Security): он ставит признак в запрос, а `RestAuthenticationEntryPoint` отдаёт чистый 403 вместо 401;
- UI: `pages/admin/AdminUsersPage.tsx` — поиск, бейджи статуса («заблокирован», «активен», «ждет подтверждения e-mail»), кнопка «Заблокировать/Разблокировать» с подтверждением и подсказкой, что сессии будут отозваны; своя строка блокируется.

---

## 6. Защита от ботов и атак

### Лимиты частоты запросов
- `security/InMemoryIpRateLimiter` — скользящее окно в памяти процесса, счётчики **разделены по областям**: `public` (80/мин), `auth` (5/мин на вход), `register` (5/мин на регистрацию), поэтому серфинг по каталогу не «съедает» лимит входа;
- `security/RateLimitFilter` зарегистрирован **до** всего фильтра Spring Security (`addFilterBefore(rateLimitFilter, LogoutFilter.class)`): отказ по лимиту случается до разбора JWT, до обращений к БД и до дорогого BCrypt;
- ответ — `429 Too Many Requests` + `Retry-After` (в секундах), тело в привычном формате `ApiError`;
- `/api/images/**` под лимит не попадает: одна страница каталога тянет десятки картинок, живой человек получал бы 429 просто листая товары;
- `security/ClientIpResolver` берёт `X-Forwarded-For` **только** при `security.rate-limit.trust-proxy-headers=true` (иначе адрес подделывается одним заголовком — это отдельный тест в `RateLimitFilterTest`).

### Капча Cloudflare Turnstile
- `config/TurnstileProperties` + `services/TurnstileService`: если `captcha.turnstile.secret-key` пустой — капча **выключена** (локальная разработка и CI не зависят от внешних сервисов); как только ключ задан, сервер проверяет токен сам и **отказывается регистрировать при недоступности Cloudflare** (fail-closed, иначе капча превращается в декорацию);
- `GET /api/auth/captcha-status` — фронтенд узнаёт, нужна ли капча, и не рисует лишний блок (см. `pages/RegisterPage.tsx`, `components/TurnstileWidget.tsx`);
- `services/RegistrationGuard` — дополнительный «дневной» лимит регистраций с одного адреса (по умолчанию 3 за 24 ч), проверяется **до** обращения в Cloudflare: дешёвая локальная проверка первой.

---

## 7. Юзабилити и вёрстка

- **быстрое «в наличии»** в таблице товаров (`.switch` → `PATCH /api/admin/tshirts/{id}/availability`, строка обновляется без перезагрузки);
- **бейдж «Заказ #4»** в таблице товаров: ссылка на заказ; находится **одним** запросом на всю страницу (`OrderRepository.findOrdersOfProducts`), а не «по запросу на строку» (`AdminProductCatalogServiceTest` прямо защищает от N+1);
- карточки позиций в заказах (админ и клиент) вместо списка текста;
- адаптивность: широкие таблицы админки получают горизонтальный скролл (`overflow-x: auto`, `min-width`), карточки статистики/воронки/рейтингов перестраиваются в 2 колонки и затем в 1, строки рейтинга складываются на мобильных;
- чище зазоры и типографика: `.page__head`, `.facts--inline`, `.warn-text`, единые отступы панелей статистики.

---

## Новые эндпоинты (сводно)

| Метод | Путь | Доступ | Смысл |
|---|---|---|---|
| GET | `/api/collections` | все | активные коллекции для баннера/чипсов |
| GET/POST/PUT/DELETE | `/api/admin/collections**` | ADMIN | CRUD полок (включая выключенные) |
| GET | `/api/admin/tshirts` | ADMIN | список с `viewsCount`, `soldInOrderId`, `disabled` |
| GET | `/api/admin/tshirts/{id}` | ADMIN | карточка, включая скрытую |
| PATCH | `/api/admin/tshirts/{id}/availability` | ADMIN | быстрый переключатель «в наличии» |
| GET | `/api/admin/orders?status=` | ADMIN | фильтр по статусу |
| POST | `/api/admin/orders/{id}/restore-items` | ADMIN | ручной возврат товаров в продажу |
| GET | `/api/admin/orders/statuses` | ADMIN | список статусов для UI |
| GET | `/api/admin/users` | ADMIN | пользователи + статусы |
| PATCH | `/api/admin/users/{id}/ban` | ADMIN | бан/разбан + отзыв refresh-токенов |
| GET | `/api/admin/analytics/dashboard` | ADMIN | дашборд + воронка + топы |
| GET | `/api/admin/analytics/promo-usage` | ADMIN | отчёт по промокодам |
| POST | `/api/track/visit`, `/api/track/cart-add` | все (с согласием) | события воронки |
| GET | `/api/auth/captcha-status` | все | нужна ли капча + site key |

---

## База данных

Hibernate запущен с `spring.jpa.hibernate.ddl-auto=update`, поэтому схема доезжается автоматически (ничего не удалялось и не пересоздавалось):

- новая таблица `collections` (`id`, `name` unique, `description`, `badge_color`, `active`, `created_at`);
- новая таблица `tracking_events` (`event_type`, `visitor_key`, `tshirt_id`, `event_date`, `dedupe_key` unique, `created_at`) + индексы `idx_tracking_type`, `idx_tracking_created`;
- `tshirts`: `+ collection_id` (FK на `collections`), `+ views_count bigint not null default 0`;
- `users`: `+ banned boolean default false`.

⚠️ Эти DDL уже применены к вашей локальной БД `appshop` прогоном `AppShopRunTest` 25.11.2026 (в логе видны только `create table` и `alter table add column`/`add constraint`). На других окружениях это произойдёт при первом старте приложения.

---

## Настройки и переменные окружения

```
# лимиты запросов
security.rate-limit.enabled              ${RATE_LIMIT_ENABLED:true}
security.rate-limit.public-requests-per-minute   ${RATE_LIMIT_PUBLIC_PER_MIN:80}
security.rate-limit.auth-requests-per-minute     ${RATE_LIMIT_AUTH_PER_MIN:5}
security.rate-limit.block-minutes        ${RATE_LIMIT_BLOCK_MIN:1}
security.rate-limit.sweep-minutes        ${RATE_LIMIT_SWEEP_MIN:10}
security.rate-limit.trust-proxy-headers  ${TRUST_PROXY_HEADERS:false}   # true только за nginx/Cloudflare

# капча
captcha.turnstile.site-key               ${TURNSTILE_SITE_KEY:}         # пусто = капча выключена
captcha.turnstile.secret-key             ${TURNSTILE_SECRET_KEY:}
captcha.turnstile.registrations-per-ip-per-day  ${REGISTRATIONS_PER_IP_PER_DAY:3}

# аналитика
app.analytics.top-size                   ${ANALYTICS_TOP_SIZE:5}
```

CORS: в разрешённые заголовки добавлен `X-Visitor-Id`, иначе браузер не пропустит запрос с этим заголовком.

Сейчас `TURNSTILE_SECRET_KEY`/`SITE_KEY` не заданы → капча выключена. Чтобы включить: получить ключи на Cloudflare Turnstile, положить в `secrets.properties` или в переменные окружения, после этого фронтенд сам начнёт показывать виджет.

---

## Проверки (фактические результаты)

```
bash scripts/mvn.sh -o -q test-compile                      → успешно
bash scripts/mvn.sh -o test -Dtest='!AppShopRunTest' ...     → Tests run: 246, Failures: 0, Errors: 0, Skipped: 0
bash scripts/mvn.sh -o test -Dtest=AppShopRunTest ...        → Tests run: 4, Failures: 0, Errors: 0  (реальная MySQL)
cd frontend && npm run build                                → tsc --noEmit чистый, dist: CSS 21.63 kB, JS 308.01 kB
```

Всего тестов бэкенда: **250** (246 без MySQL + 4 интеграционных на MySQL).

Новые тестовые классы и что они защищают:

| Класс | Тестов | Что доказывает |
|---|---|---|
| `repositories/RepositoryQueryTest` | 24 | JPQL/спецификации/уникальные индексы на реальном H2 (MODE=MySQL): витрина не видит скрытое, а админ видит; комбинация фильтров; атомарный `increaseViewsCount`; отвязка товаров при удалении коллекции; дедупликация событий; блокировка возврата уже проданного товара; отчёты по промокодам |
| `security/RateLimitFilterTest` | 8 | 429 + `Retry-After`, пропуск статики/картинок, поддельный `X-Forwarded-For` не работает без доверия прокси |
| `security/InMemoryIpRateLimiterTest` | 9 | скольжение окна, изоляция областей `public`/`auth`/`register`, блокировка на `block-minutes` |
| `services/AnalyticsServiceTest` | 10 | отменённые не портят выручку/скидки/промокоды, пустой магазин без деления на ноль, группировка кодов без учёта регистра |
| `services/CollectionServiceTest` | 11 | нормализация имени и HEX-цвета, уникальность без учёта регистра, удаление не трогает товары |
| `services/TrackingServiceTest` | 11 | «не более одного события в день на пару посетитель+товар», гонка не превращается в ошибку клиента, санитизация ключа |
| `services/AdminProductCatalogServiceTest` | 10 | один запрос за заказами на всю страницу (нет N+1), быстрый переключатель наличия |
| `services/UserAdminServiceTest` | 5 | отзыв refresh-токенов при бане, запрет бана самого себя |
| `services/TurnstileServiceTest` | 7 | выключено без ключа, отказ при плохом токене, fail-closed при недоступности Cloudflare |
| `services/RegistrationGuardTest` | 7 | суточный лимит регистраций по IP проверяется до обращения в Cloudflare |

---

## Как посмотреть руками (короткий сценарий)

1. Приватное окно → открыть каталог → снизу появится cookie-баннер. Нажать «Отклонить» → во вкладке Network не будет запросов `/api/track/**` и у `GET /api/tshirts/{id}` не будет `X-Visitor-Id`.
2. Принять → открыть карточку товара → «Обновить» в карточке счётчик просмотров; в админке «Статистика» вырастут «Просмотры» и шаг воронки.
3. Заведомо больше 5 неудачных входов подряд → шестой даёт `429` и фронтенд просит подождать минуту.
4. У любого пользователя нажать «Заблокировать» → его текущая вкладка в течение минуты (после истечения access-токена) теряет доступ, повторный вход отвечает `403 Аккаунт заблокирован`, а в списке он помечен красным.
5. Заказ → «Отменён» → товары остаются невидимыми для покупки; появляется кнопка «вернуть товары в продажу»; при возврате уже проданного в другом заказе товара админ получает список причин.
6. «Коллекции» → создать дроп с цветом → назначить товару → в каталоге появляется баннер/чипсы и цветные бейджи на карточках.

---

## 1. Что сделано (кратко)

**Бэкенд, новые файлы:** `config/{RateLimitProperties,RateLimitConfig,TurnstileProperties}.java`, `entities/{Collection,TrackingEvent}.java`, `dto/{CollectionDto,CollectionFormRequest,AdminTShirtDto,AvailabilityRequest,BanRequest,CaptchaStatusDto,AnalyticsDashboardDto,PromoCodeUsageDto,RestoreItemsDto,TrackingRequest}.java`, `repositories/{CollectionRepository,TrackingEventRepository}.java`, `repositories/projection/{OrderLineRow,FavoriteCountRow,TshirtOrderRow}.java`, `security/{ClientIpResolver,InMemoryIpRateLimiter,RateLimitFilter,AccountStatusGuard,VisitorKeyFactory}.java`, `services/{CollectionService,AdminProductCatalogService,TrackingService,TrackingEventWriter,AnalyticsService,TurnstileService,RegistrationGuard,UserAdminService}.java`, `rest/{CollectionsController,AdminCollectionController,AdminAnalyticsController,TrackingController}.java`.

**Бэкенд, изменено:** `SecurityConfig`, `JwtAuthenticationFilter`, `RestAuthenticationEntryPoint`, `ApiExceptionHandler`, `entities/{TShirt,User}`, `repositories/{TShirtRepository,OrderRepository,UserRepository}`, `mappers/{TShirtMapper,OrderMapper,UserMapper}`, `services/{TShirtService,OrderService,AuthService,UserService}`, `specification/TShirtSpecifications`, контроллеры `TShirtController`, `AuthController`, `AdminTShirtController`, `AdminOrderController`, `AdminUserController`, DTO `TShirtDto`, `TShirtDetailsDto`, `TShirtFormRequest`, `OrderDto`, `UserDto`, `RegisterRequest`, `application.properties`.

**Фронтенд, новые файлы:** `api/{adminProducts,collections,adminAnalytics,tracking}.ts`, `utils/{consent,visitorId}.ts`, `components/{CookieConsentBanner,CollectionsStrip,OrderItemsList,TurnstileWidget}.tsx`, `components/analytics/{StatCards,FunnelPanel,TopProductsPanel,PromoUsagePanel}.tsx`, `pages/admin/{AdminAnalyticsPage,AdminCollectionsPage,CollectionFormPanel}.tsx`.

**Фронтенд, изменено:** `App.tsx` (роуты `/admin/collections`, `/admin/analytics`), `components/{Header,Layout,ProductCard}.tsx`, `context/CartContext.tsx`, `pages/{CatalogPage,ProductPage,OrderDetailsPage,RegisterPage}.tsx`, `pages/admin/{AdminProductsPage,AdminProductFormPage,AdminOrdersPage,AdminUsersPage}.tsx`, `api/{catalog,admin,auth}.ts`, `types/api.ts`, `utils/format.ts`, `styles/{components,pages}.css`.

---

## 2. Как это работает (теория простыми словами)

- **Фильтр против JWT-фильтра.** `RateLimitFilter` — самый ранний фильтр: он имеет право отказать, не понимая, кто пользователь. Поэтому лимит считается по адресу, а не по «логину»: атакующий ещё не потратил ни секунды на BCrypt и БД, а уже получает 429.
- **Почему «перед LogoutFilter».** Spring Security умеет вставлять фильтр относительно тех, чей порядок ему известен. На собственный класс-якорь ссылаться нельзя («does not have a registered order») — `LogoutFilter` самый первый из «известных», поэтому вставка даёт начало цепочки.
- **Бан в фильтре, ответ в EntryPoint.** Внутри сервлет-фильтра исключение не дойдёт до `@RestControllerAdvice`: его перехватывает цепочка Spring Security. Поэтому фильтр только помечает запрос, а 403 отдаёт `AuthenticationEntryPoint`.
- **Атомарный `views_count`.** `UPDATE ... SET views_count = views_count + 1` вместо «прочитать → сложить → записать»: два одновременных просмотра не теряют единицу.
- **Уникальный `dedupe_key`.** Анти-спам держит база, а не «проверка if exists в коде»: две параллельные вкладки не создадут два просмотра.
- **Cookie-согласие как разрешение, а не настройка сервера.** `localStorage` живёт в браузере, поэтому решение принимется там же: «отказал» — запросов просто нет. Сервер остаётся слепым к согласию, но не хранит ПДн.
- **Капча fail-closed.** Если Cloudflare недоступен, безопаснее отказать одному посетителю, чем открыть вход спам-боту.
- **Отмена ≠ возврат на полку.** Разные операции: первая меняет финансы/статус, вторая — складской остаток, и у второй есть причины отказа.

---

## 3. На что обратить внимание (посмотреть руками)

1. `src/main/java/com/example/appShop/services/OrderService.java` → метод `restoreItemsToSale(long)` и `OrderRepository.findProductIdsSoldInOtherOrders(...)`: там вся «честность» возврата товаров в продажу.
2. `src/main/java/com/example/appShop/security/RateLimitFilter.java` + `InMemoryIpRateLimiter.java` (и как он воткнут в `SecurityConfig`): как устроены области лимитов, `Retry-After` и почему `X-Forwarded-For` доверяется только за прокси.

---

## Известные ограничения / что дальше

- **Лимиты в памяти процесса.** Правильно для одного инстанса; при горизонтальном масштабировании нужен внешний счётчик (Redis).
- **Salt ключа посетителя живёт до перезапуска процесса** — это фолбэк-механизм без согласия; устойчивая идентичность обеспечивается `visitorId` в `localStorage`.
- **Таблица `tracking_events` растёт.** Нужна периодическая чистка (например, `@Scheduled` раз в сутки удалять записи старше 90–180 дней) — сейчас её нет.
- **Воронка ≠ 100% точна для отказавшихся от cookies**: их заказы видны в БД, а событий нет; в панели это честно подписано.
- **Turnstile не настроен** (ключи пустые) — капча выключена.
- **Access-токен нельзя отозвать мгновенно** (stateless JWT): бан работает через отзыв refresh-токенов и максимум 15 минут жизни access-токена.
- **`Order` ↔ `TShirt` — `@ManyToMany`** (без отдельной сущности `OrderItem`): цена позиции не «замораживается» в заказе, что отдельная задача на будущем рефакторинге.

---

## Архив: отчёт от 23.11.2026 (прошлая итерация)

Скрытые товары для админа, плавное листание фото, подтверждение e-mail по PIN-коду, промокоды и скидки, избранное и профиль. Текст ниже — без изменений.



Дата: 23.11.2026
Ветка/рабочая копия: `D:\website\ClothesWebsite-master` (изменения **не закоммичены** — по правилу проекта коммит только с вашего разрешения).

В работе реализованы пять блоков: (1) доступность скрытых товаров для админа, (2) плавное листание фото, (3) подтверждение e-mail по 6-значному PIN-коду, (4) промокоды и скидки, (5) избранное + профиль. В конце — результаты проверок (сборка фронтенда и тесты бэкенда) и список мест, которые стоит посмотреть вручную.

---

### 0. Как проверить результат

```bash
# бэкенд: компиляция + все тесты, кроме тех, что требуют MySQL
bash scripts/mvn.sh -o test -Dtest='!AppShopRunTest' -DfailIfNoTests=false

# бэкенд: полный старт контекста (требует запущенный MySQL и БД appshop)
bash scripts/mvn.sh -o test -Dtest=AppShopRunTest -DfailIfNoTests=false

# фронтенд: строгая типизация + production-сборка
cd frontend && npm run build
```

`AppShopRunTest` я не запускал: он поднимает приложение на вашей рабочей БД `appshop`, а это значит автоматическое применение DDL (см. раздел «Изменения в базе данных»). Для проверки «собирается ли контекст» без MySQL я добавил `ApplicationContextSmokeTest` на H2 — он входит в команду выше.

---

### 1. Скрытые товары: админ может править и возвращать в продажу

#### Проблема
Товар снимается с продажи «мягко» (`t_shirt.is_disabled = true`). Публичная карточка `GET /api/tshirts/{id}` такой товар скрывает (404), поэтому форма правки в админке не могла открыть скрытую позицию, а в списке `/admin/products` у скрытой позиции была только кнопка «скрыть» — вернуть товар обратно было нечем.

#### Что сделано в бэкенде
- `services/TShirtService.java`
  - публичный `getDetails(id)` как и раньше фильтрует `!isDisabled()` — приватность публичного эндпоинта не менялась;
  - добавлен приватный `getForAdminOrThrow(id)` (без фильтра по `is_disabled`) и публичный `getDetailsForAdmin(id)`;
  - `update(...)` и `setPreviewImage(...)` читают товар через «админский» путь, иначе правку скрытой позиции невозможно сохранить;
  - добавлен `restore(id)` — снимает `is_disabled` **и** ставит `available = true` (иначе товар вернулся бы в каталог со статусом «продано»);
  - добавлены тесты: `adminSeesHiddenProductInOrderToEditIt`, `hiddenProductIsEditableByPut`, `restoreReturnsHiddenProductToSale`.
- новый `rest/AdminTShirtController.java` (`/api/admin/tshirts`):
  - `GET  /api/admin/tshirts/{id}` — карточка любой позиции, включая скрытую;
  - `PATCH /api/admin/tshirts/{id}/restore` — вернуть в продажу.

Почему отдельный контроллер, а не параметр `?admin=true` в публичном `GET /api/tshirts/{id}`: весь путь `/api/admin/**` закрыт ролью `ADMIN` в `config/SecurityConfig.java`, поэтому внутри контроллера проверок прав нет вообще, а самый чувствительный к приватности публичный эндпоинт остаётся «глупым» и безопасным.

#### Что сделано во фронтенде
- `api/catalog.ts`: `getTShirtForAdmin(id)`, `restoreTShirt(id)`.
- `pages/admin/AdminProductFormPage.tsx`: читает товар через `getTShirtForAdmin` (скрытый открывается в форме правки), показывает бейдж «скрыт из каталога» и кнопку «вернуть в продажу».
- `pages/admin/AdminProductsPage.tsx`:
  - у скрытой позиции вместо «скрыть» кнопка **«вернуть в продажу»**;
  - после скрытия строка не исчезает из списка при включённом «показывать скрытые» — меняется `disabled/available` локально;
  - общие для списка операции переведены на `load()`-колбэк, чтобы состояние страницы обновлялось одинаково после любого действия.

---

### 2. Плавное листание фотографий

Вместо мгновенной подмены `src` используется «лента» из всех фото и сдвиг на `translateX`.

- `components/ProductCard.tsx`: все картинки карточки стоят в рядом лежащем ряду
  `<div class="card__viewport"><div class="card__track" style="transform: translateX(-N*100%)">…</div></div>`;
  стрелки меняют индекс — CSS рисует переход `transition: transform 300ms ease-in-out`.
- `pages/ProductPage.tsx`: тот же приём (`.product__viewport` / `.product__track` / `.product__slide`), активный кадр синхронизирован с миниатюрами и счётчиком «N/M».
- `styles/components.css`, `styles/pages.css`: новые классы + правило доступности
  `@media (prefers-reduced-motion: reduce) { .card__track, .product__track { transition: none } }`.

Почему так, а не «кроссфейд» двух картинок: все фото уже лежат в DOM (следующее не грузится в момент нажатия), анимация делает только композитор, и поведение одинаковое в каталоге и на странице товара.

---

### 3. Подтверждение e-mail: 6-значный PIN-код

#### Бэкенд
- `pom.xml`: добавлен `spring-boot-starter-mail`.
- `src/main/resources/application.properties`: блок `spring.mail.*` (host/port/username/password подставляются из `secrets.properties` / переменных окружения `MAIL_*`), `app.mail.from`, `app.verification.enabled`, `app.verification.code-ttl=PT24H`.
- `config/AppMailProperties.java`, `config/VerificationProperties.java` — конфигурационные записи (подхватывает `@ConfigurationPropertiesScan`, объявленный на `AppShopRun`).
- `services/EmailSender.java` — единственная точка отправки писем. Бин `JavaMailSender` существует только при заданном `spring.mail.host`; если хоста нет или отправка упала по `MailException`, метод честно возвращает `false` и регистрация **не** падает.
- `services/EmailVerificationService.java`:
  - PIN — 6 цифр из `SecureRandom` (не `java.util.Random`: код должен быть непредсказуемым);
  - `issueCode(user)` ставит `enabled = false`, сохраняет код и отметку времени, отправляет письмо, а если писем нет — печатает в лог:
    `PIN-код подтверждения регистрации для <email> (введите его в форме «Подтверждение e-mail»): 123456`;
  - `confirm(user, code)` — сверка с учётом срока жизни кода (`app.verification.code-ttl`) и стирание кода после подтверждения; формулировки ошибок намеренно не подсказывают, существовал ли код раньше;
  - `normalizeCode` принимает код с пробелами и дефисами.
- `services/AuthService.java`:
  - `register(...)` возвращает `VerificationStatusDto` и **не выдаёт токены**;
  - `verifyEmail(...)` активирует аккаунт и возвращает `AuthResponse` (access + refresh);
  - `resendVerificationCode(...)` — повторная отправка, ответ одинаковый для существующего и неизвестного e-mail (не подсказываем, зарегистрирован ли адрес);
  - `login(...)` перехватывает `DisabledException` Spring Security и превращает в `AccountNotVerifiedException` (**403**, а не 401) — фронтенд по коду понимает «нужен PIN», а не «неверный пароль»;
  - `refresh(...)` отзывает refresh-токен, если аккаунт не подтверждён.
- `security/UserPrincipal.java`: `isEnabled()` читается из пользователя, поэтому неподтверждённый
  аккаунт не проходит аутентификацию (`services/CustomUserDetailsService.java` не менялся —
  он и раньше собирал `UserPrincipal` из всех флагов пользователя).
- `exceptions/AccountNotVerifiedException.java` — 403 с человекочитаемым сообщением.
- `rest/AuthController.java` + `config/SecurityConfig.java`: новые публичные ручки и разрешения.

| Метод | URL | Ответ |
|---|---|---|
| POST | `/api/auth/register` | `VerificationStatusDto` (без токенов) |
| POST | `/api/auth/verify-email` | `AuthResponse` (токены выдаются здесь) |
| POST | `/api/auth/verification-code` | `VerificationStatusDto` (повторная отправка PIN) |
| POST | `/api/auth/login` | `AuthResponse`, **403** `AccountNotVerifiedException` для неподтверждённых |
| POST | `/api/auth/refresh` | `AuthResponse`; при неподтверждённом аккаунте токен отзывается |

#### Фронтенд
- `api/auth.ts`: `register()` → `VerificationStatusDto`, добавлены `verifyEmail()` и `resendVerificationCode()`.
- `context/AuthContext.tsx`: `register` больше не логинит (не трогает `user`), добавлен `verifyEmail`, который применяет токены и ставит пользователя.
- `pages/RegisterPage.tsx`: после регистрации переход на `/verify-email` (если бэкенд ждёт PIN) или на `/login` (если подтверждение отключено конфигом); подсказка, что локально PIN печатается в консоли.
- новый `pages/VerifyEmailPage.tsx` (публичный маршрут `/verify-email`): поле на 6 цифр, повторная отправка, вход в аккаунт сразу после подтверждения.
- `pages/LoginPage.tsx`: при 403 сохраняет введённый e-mail и показывает ссылку «ввести PIN-код из письма».

#### Конфигурация SMTP
`secrets.properties` (и образец `secrets.properties.example`):

```properties
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your.account@gmail.com
MAIL_PASSWORD=пароль-приложения
MAIL_FROM=noreply@clothes.shop
VERIFICATION_ENABLED=true    # false — отключить подтверждение (только для разработки)
```

Для Gmail нужен именно «пароль приложения», а не пароль аккаунта. Пока `MAIL_HOST` пустой, приложение работает без почты и печатает PIN в лог — это штатный режим локальной разработки.

---

### 4. Промокоды и скидки

#### Бэкенд
- `entities/PromoCode.java` — таблица `promo_codes`: `code` (уникальный, 40), `discount_percent`, `active`, `expires_at` (null = бессрочный), `created_at`; методы `isExpiredAt(...)`, `isUsable(...)`.
- `repositories/PromoCodeRepository.java` — производные методы `findFirstByCodeIgnoreCase`, `existsByCodeIgnoreCase`, `findAllByOrderByIdDesc` (Hibernate строит `upper(code) = upper(?)`, поэтому регистр не важен и в MySQL, и в H2 на тестах).
- `services/PromoCodeService.java`:
  - CRUD для админа; код нормализуется в верхний регистр; при пустом коде админа генерируется 8-символьный из алфавита без похожих знаков (`ABCDEFGHJKLMNPQRSTUVWXYZ23456789`, без `O/0`, `I/1`);
  - проверка покупателя `validate(PromoValidationRequest)`: код обязан существовать, быть включённым и не просроченным; суммы (`TotalsDto`) считаются **по ценам из базы**, а не по присланным фронтендом;
  - `resolveOrNull(code)` для заказа: пустой код → `null` (скидки нет), неверный → `InvalidRequestException` с причиной («не найден», «выключен администратором», «срок действия истёк»).
- `dto/TotalsDto.java` — `subtotal`, `discountPercent`, `discountAmount`, `total`; округление `BigDecimal`/`HALF_UP` до 2 знаков, итог не может быть отрицательным.
- `entities/Order.java` — новые поля `promo_code` и `discount_percent`: в заказ кладётся **слепок** (строка и процент на момент покупки), а не ссылка на `PromoCode`, иначе переименование или удаление кода изменило бы историю уже оплаченных заказов.
- `services/OrderService.java` — перед сохранением заказа вызывает `promoCodeService.resolveOrNull(...)`, сохраняет слепок; суммы заказа считает `mapper/OrderMapper.java` через `TotalsDto.of(...)` из цен, лежащих в позициях заказа.
- `dto/OrderDto.java` — добавлены `promoCode`, `discountPercent`, `discountAmount`, `total`.
- контроллеры: `rest/PromoCodeController.java` (`POST /api/promo-codes/validate`, авторизованные пользователи) и `rest/AdminPromoCodeController.java` (`GET/POST/PUT/DELETE /api/admin/promo-codes`, доступ только `ADMIN`).

#### Фронтенд
- новый `hooks/usePromo.ts` — вся логика: запрос валидации на сервер, повторная валидация при изменении состава корзины (подпись из id товаров), состояния `applied/error/busy`, хранение введённого кода.
- `context/CartContext.tsx` — в `localStorage` сохраняется **только строка кода** (`clothes.promo`); проценты и суммы никогда не берутся из локального хранилища.
- новый `components/PromoCodeBox.tsx` — форма ввода кода + `PromoTotals` (подытог, скидка, к оплате).
- `pages/CartPage.tsx` и `pages/CheckoutPage.tsx` — блок промокода, суммы с сервера, `promoCode` в теле `POST /api/orders`.
- новый `components/OrderTotals.tsx` — единый блок сумм («Товары / Промокод −N% / Итого») для карточки заказа; `pages/OrderDetailsPage.tsx` больше не складывает цены сам.
- `pages/admin/AdminOrdersPage.tsx` — колонка суммы берётся из `order.total`, под ней подпись промокода и скидки.
- новые страницы админки: `pages/admin/AdminPromoCodesPage.tsx` (список, удаление, флеш-сообщения) и `pages/admin/PromoCodeFormPanel.tsx` (создание/правка; код можно оставить пустым — сгенерирует сервер; срок действия в `datetime-local` конвертируется в ISO `Instant`).
- `App.tsx`: маршрут `/admin/promo-codes` (только `ADMIN`), в шапке ссылка «Промокоды».

Пример проверки со стороны клиента:

```
POST /api/promo-codes/validate
{ "code": "sale2024", "tshirtIds": [12, 31] }

→ { "code": "SALE2024", "discountPercent": 15, "expiresAt": null,
    "totals": { "subtotal": 3980.0, "discountPercent": 15,
                "discountAmount": 597.0, "total": 3383.0 },
    "message": "Промокод SALE2024 применён: скидка 15%" }
```

---

### 5. Избранное и профиль

#### Бэкенд
- `entities/User.java` — `favorites` (`ManyToMany` с `TShirt`, таблица-связка `user_favorites`
  с колонками `user_id` и `tshirt_id`, `FetchType.LAZY`). Связь односторонняя: `TShirt` не знает о
  пользователях, иначе ленивые загрузки и сериализация начали бы «ходить» по кругу.
- новый `services/FavoriteService.java`:
  - владелец берётся **только** из principal из токена (id пользователя не передаётся в запросе);
  - `list`, `favoriteIds` (легкий ответ для отрисовки сердечек), `add` (идемпотентен), `remove`;
  - скрытый товар (`is_disabled`) в избранное добавить нельзя;
  - `remove` сравнивает по `getId().equals(id)`, а не объекты: у `TShirt` нет `equals/hashCode`, а сравнение прокси-объектов Hibernate ненадёжно;
  - методы помечены `@Transactional`, иначе lazy-коллекция `favorites` «потечёт» вне сессии (`spring.jpa.open-in-view=false`).
- новый `rest/FavoriteController.java`:

| Метод | URL | Ответ |
|---|---|---|
| GET | `/api/favorites` | список `TShirtDto` |
| GET | `/api/favorites/ids` | `number[]` |
| POST | `/api/favorites/{tshirtId}` | 204 |
| DELETE | `/api/favorites/{tshirtId}` | 204 |

- `config/SecurityConfig.java`: `/api/favorites/**` и `/api/promo-codes/**` явно требуют аутентификации.

#### Фронтенд
- новый `context/FavoritesContext.tsx` — состояние избранного, привязанное к аккаунту: `ids`, `loaded`, `isFavorite`, `toggle`, `reload`, `error`, `dismissError`; загрузка при смене пользователя; у гостя список пуст; переключение **оптимистичное** (сердечко реагирует сразу, при ошибке — откат и текст ошибки).
- новый `components/FavoriteButton.tsx` — сердце (outline/filled) с `preventDefault/stopPropagation`, чтобы клик по сердцу не вёл на страницу товара.
- `components/ProductCard.tsx` и `pages/ProductPage.tsx` — сердце поверх изображения; состояние синхронно в каталоге, на странице товара и в профиле.
- новый `components/FavoritesNotice.tsx` — сообщение об ошибке избранного (гость → подсказка «войти» + крестик), один компонент для каталога, карточки товара и профиля.
- новый `pages/ProfilePage.tsx` — маршруты `/profile` с вкладками «Мои заказы» / «Избранное»; активная вкладка живёт в URL (`/profile?tab=favorites`), поэтому работает обнов страницы и прямые ссылки; у вкладки «Избранное» — счётчик.
- новые/изменённые компоненты: `components/OrdersPanel.tsx` (общий список заказов для `/orders` и профиля, суммы из DTO), `components/FavoritesPanel.tsx` (список избранных карточек; фильтрация по `ids` из контекста — при снятии из избранного карточка исчезает без повторного запроса), `components/OrderTotals.tsx`.
- `components/Header.tsx`: имя пользователя стало ссылкой на `/profile`, добавлены пункты «Профиль», «Мои заказы», админу — «Промокоды».
- `pages/OrdersPage.tsx` упрощён до обёртки над `OrdersPanel` (меньше дублирования, −69 строк).

---

### Изменения в базе данных

`spring.jpa.hibernate.ddl-auto=update`, поэтому при следующем старте приложения Hibernate сам добавит **только новые объекты**, ничего не удаляя:

| Объект | Что именно |
|---|---|
| таблица `promo_codes` | `id`, `code` (UNIQUE, 40), `discount_percent`, `active`, `expires_at`, `created_at` |
| таблица `user_favorites` | `user_id`, `tshirt_id` — связка «пользователь ↔ избранный товар» |
| таблица `orders` | новые колонки `promo_code` (40) и `discount_percent` — обе nullable |
| таблица `users` | новая колонка `verification_code_sent_at` — nullable |

Существующие колонки и таблицы не менялись и не удалялись: колонка `users.verification_code`
sуществовала и раньше, добавлена только отметка времени выдачи кода. Существующие заказы и
пользователи остаются рабочими: новые поля у старых строк = `NULL`, а `OrderMapper` трактует
отсутствующий процент как 0.

---

### Тесты: результаты

Фронтенд (`cd frontend && npm run build`): `tsc --noEmit` без ошибок (строгий режим + `noUnusedLocals`), Vite-сборка успешна — `dist/assets/index-*.css 15.50 kB` (gzip 3.67 kB), `dist/assets/index-*.js 282.27 kB` (gzip 91.70 kB).

Бэкенд (`bash scripts/mvn.sh -o test -Dtest='!AppShopRunTest' -DfailIfNoTests=false`): **130 тестов, 0 падений, 0 ошибок**, BUILD SUCCESS.

| Тест | Кол-во | Что покрывает |
|---|---|---|
| `ApplicationContextSmokeTest` (новый, H2) | 5 | старт контейнера без MySQL: новые бины и контроллеры на месте, `@ConfigurationPropertiesScan` подобрал `VerificationProperties`/`AppMailProperties`, `EmailSender.isSmtpConfigured() == false`, таблица `promo_codes` читается |
| `PromoCodeRepositoryTest` (новый, H2) | 4 | derived-запросы с `IgnoreCase`, уникальность `code` на уровне БД, порядок «сначала свежие» |
| `EmailVerificationServiceTest` (новый) | 7 | формат PIN из 6 цифр, активация, срок жизни кода, сообщения без утечки «был/не был код» |
| `FavoriteServiceTest` (новый) | 8 | идемпотентность добавления, удаление по id, запрет скрытых товаров, владелец из principal |
| `PromoCodeServiceTest` (новый) | 12 | CRUD, формат сгенерированного кода, защита от дублей, отказ просроченных/выключенных/неизвестных кодов, пересчёт сумм по ценам из БД |
| `AuthServiceTest` (обновлён) | 13 | регистрация без токенов, подтверждение PIN, повторная отправка, 403 для неподтверждённого входа, отзыв refresh |
| `OrderServiceTest` (обновлён) | 17 | слепок промокода в заказе, заказ без скидки, ошибка при неверном коде |
| `TShirtServiceTest` (обновлён) | 23 | скрытый товар не виден публично, виден админу, редактируется, `restore` возвращает в продажу |
| остальные (security, users, images) | 49 | регрессия прежних блоков |

`AppShopRunTest` (требует MySQL) я не запускал — он стартует приложение на вашей рабочей БД и применит DDL из раздела выше.

---

### Что стоит открыть и изучить самостоятельно

1. **`src/main/java/com/example/appShop/services/EmailSender.java` + `services/EmailVerificationService.java`**
   Ключевая идея всего блока подтверждения: `JavaMailSender` — бин с условным автоконфигурированием (`MailSenderAutoConfiguration` создаёт его только при непустом `spring.mail.host`). Поэтому проверка `ObjectProvider<JavaMailSender>` + возврат `false` даёт «деградацию без падения»: приложение работает и без почты, а PIN попадает в лог. Сравните с тем, как `AuthService.login` перехватывает `DisabledException` — это тот же приём: низкоуровневый факт переводим в понятный HTTP-ответ.

2. **`src/main/java/com/example/appShop/services/PromoCodeService.java` + `dto/TotalsDto.java` + `mapper/OrderMapper.java`**
   Здесь видно, как в проекте проходит граница «фронтенду не доверяем»: клиент присылает только id товаров и строку кода, а подытог, скидку и итог считает сервер (и то же самое делает при создании заказа). Обратите внимание на `BigDecimal`/`RoundingMode.HALF_UP` в `TotalsDto` — обычное `double`-складывание дало бы копейки погрешности в суммах заказа.

3. **`frontend/src/context/FavoritesContext.tsx` и `frontend/src/hooks/usePromo.ts`**
   Первый показывает оптимистичное обновление UI с откатом при ошибке, второй — что клиент хранит только код промокода, а суммы всегда приходят с сервера. Сравните с `context/CartContext.tsx`: корзина живёт в `localStorage`, избранное — в MySQL, отсюда и разная стратегия.

### Известные ограничения / что дальше

- `users.verification_code` хранится в открытом виде (PIN короткий и живёт 24 ч, но по идее его стоит хэшировать, как пароль). Хэширование — отдельная задача: тогда нельзя будет показывать код в логе «как есть», и fallback для локальной разработки придётся переделать.
- Повторная отправка PIN не имеет rate-limit: защиту от «спам-повторов» и от перебора кода (например, 5 попыток на аккаунт) стоит добавить отдельно.
- `spring.jpa.hibernate.ddl-auto=update` удобен в разработке, но для боя нужны миграции (Flyway/Liquibase): Hibernate не изменит и не удалит колонки, которые уже существуют с другими правилами.
- Скинута только одна скидка (один промокод на заказ); суммировать несколько кодов и делать «скидка от категории» текущая модель `Order` (один процент-слепок) не умеет.
- Загруженность фаворитов: `GET /api/favorites/ids` отдаёт все id без пагинации — при десятках тысяч избранных понадобится постраничность.
