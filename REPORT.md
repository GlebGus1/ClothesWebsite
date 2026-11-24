# Отчёт: доработка интернет-магазина ClothesShop

Дата: 23.11.2026
Ветка/рабочая копия: `D:\website\ClothesWebsite-master` (изменения **не закоммичены** — по правилу проекта коммит только с вашего разрешения).

В работе реализованы пять блоков: (1) доступность скрытых товаров для админа, (2) плавное листание фото, (3) подтверждение e-mail по 6-значному PIN-коду, (4) промокоды и скидки, (5) избранное + профиль. В конце — результаты проверок (сборка фронтенда и тесты бэкенда) и список мест, которые стоит посмотреть вручную.

---

## 0. Как проверить результат

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

## 1. Скрытые товары: админ может править и возвращать в продажу

### Проблема
Товар снимается с продажи «мягко» (`t_shirt.is_disabled = true`). Публичная карточка `GET /api/tshirts/{id}` такой товар скрывает (404), поэтому форма правки в админке не могла открыть скрытую позицию, а в списке `/admin/products` у скрытой позиции была только кнопка «скрыть» — вернуть товар обратно было нечем.

### Что сделано в бэкенде
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

### Что сделано во фронтенде
- `api/catalog.ts`: `getTShirtForAdmin(id)`, `restoreTShirt(id)`.
- `pages/admin/AdminProductFormPage.tsx`: читает товар через `getTShirtForAdmin` (скрытый открывается в форме правки), показывает бейдж «скрыт из каталога» и кнопку «вернуть в продажу».
- `pages/admin/AdminProductsPage.tsx`:
  - у скрытой позиции вместо «скрыть» кнопка **«вернуть в продажу»**;
  - после скрытия строка не исчезает из списка при включённом «показывать скрытые» — меняется `disabled/available` локально;
  - общие для списка операции переведены на `load()`-колбэк, чтобы состояние страницы обновлялось одинаково после любого действия.

---

## 2. Плавное листание фотографий

Вместо мгновенной подмены `src` используется «лента» из всех фото и сдвиг на `translateX`.

- `components/ProductCard.tsx`: все картинки карточки стоят в рядом лежащем ряду
  `<div class="card__viewport"><div class="card__track" style="transform: translateX(-N*100%)">…</div></div>`;
  стрелки меняют индекс — CSS рисует переход `transition: transform 300ms ease-in-out`.
- `pages/ProductPage.tsx`: тот же приём (`.product__viewport` / `.product__track` / `.product__slide`), активный кадр синхронизирован с миниатюрами и счётчиком «N/M».
- `styles/components.css`, `styles/pages.css`: новые классы + правило доступности
  `@media (prefers-reduced-motion: reduce) { .card__track, .product__track { transition: none } }`.

Почему так, а не «кроссфейд» двух картинок: все фото уже лежат в DOM (следующее не грузится в момент нажатия), анимация делает только композитор, и поведение одинаковое в каталоге и на странице товара.

---

## 3. Подтверждение e-mail: 6-значный PIN-код

### Бэкенд
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

### Фронтенд
- `api/auth.ts`: `register()` → `VerificationStatusDto`, добавлены `verifyEmail()` и `resendVerificationCode()`.
- `context/AuthContext.tsx`: `register` больше не логинит (не трогает `user`), добавлен `verifyEmail`, который применяет токены и ставит пользователя.
- `pages/RegisterPage.tsx`: после регистрации переход на `/verify-email` (если бэкенд ждёт PIN) или на `/login` (если подтверждение отключено конфигом); подсказка, что локально PIN печатается в консоли.
- новый `pages/VerifyEmailPage.tsx` (публичный маршрут `/verify-email`): поле на 6 цифр, повторная отправка, вход в аккаунт сразу после подтверждения.
- `pages/LoginPage.tsx`: при 403 сохраняет введённый e-mail и показывает ссылку «ввести PIN-код из письма».

### Конфигурация SMTP
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

## 4. Промокоды и скидки

### Бэкенд
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

### Фронтенд
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

## 5. Избранное и профиль

### Бэкенд
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

### Фронтенд
- новый `context/FavoritesContext.tsx` — состояние избранного, привязанное к аккаунту: `ids`, `loaded`, `isFavorite`, `toggle`, `reload`, `error`, `dismissError`; загрузка при смене пользователя; у гостя список пуст; переключение **оптимистичное** (сердечко реагирует сразу, при ошибке — откат и текст ошибки).
- новый `components/FavoriteButton.tsx` — сердце (outline/filled) с `preventDefault/stopPropagation`, чтобы клик по сердцу не вёл на страницу товара.
- `components/ProductCard.tsx` и `pages/ProductPage.tsx` — сердце поверх изображения; состояние синхронно в каталоге, на странице товара и в профиле.
- новый `components/FavoritesNotice.tsx` — сообщение об ошибке избранного (гость → подсказка «войти» + крестик), один компонент для каталога, карточки товара и профиля.
- новый `pages/ProfilePage.tsx` — маршруты `/profile` с вкладками «Мои заказы» / «Избранное»; активная вкладка живёт в URL (`/profile?tab=favorites`), поэтому работает обнов страницы и прямые ссылки; у вкладки «Избранное» — счётчик.
- новые/изменённые компоненты: `components/OrdersPanel.tsx` (общий список заказов для `/orders` и профиля, суммы из DTO), `components/FavoritesPanel.tsx` (список избранных карточек; фильтрация по `ids` из контекста — при снятии из избранного карточка исчезает без повторного запроса), `components/OrderTotals.tsx`.
- `components/Header.tsx`: имя пользователя стало ссылкой на `/profile`, добавлены пункты «Профиль», «Мои заказы», админу — «Промокоды».
- `pages/OrdersPage.tsx` упрощён до обёртки над `OrdersPanel` (меньше дублирования, −69 строк).

---

## Изменения в базе данных

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

## Тесты: результаты

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

## Что стоит открыть и изучить самостоятельно

1. **`src/main/java/com/example/appShop/services/EmailSender.java` + `services/EmailVerificationService.java`**
   Ключевая идея всего блока подтверждения: `JavaMailSender` — бин с условным автоконфигурированием (`MailSenderAutoConfiguration` создаёт его только при непустом `spring.mail.host`). Поэтому проверка `ObjectProvider<JavaMailSender>` + возврат `false` даёт «деградацию без падения»: приложение работает и без почты, а PIN попадает в лог. Сравните с тем, как `AuthService.login` перехватывает `DisabledException` — это тот же приём: низкоуровневый факт переводим в понятный HTTP-ответ.

2. **`src/main/java/com/example/appShop/services/PromoCodeService.java` + `dto/TotalsDto.java` + `mapper/OrderMapper.java`**
   Здесь видно, как в проекте проходит граница «фронтенду не доверяем»: клиент присылает только id товаров и строку кода, а подытог, скидку и итог считает сервер (и то же самое делает при создании заказа). Обратите внимание на `BigDecimal`/`RoundingMode.HALF_UP` в `TotalsDto` — обычное `double`-складывание дало бы копейки погрешности в суммах заказа.

3. **`frontend/src/context/FavoritesContext.tsx` и `frontend/src/hooks/usePromo.ts`**
   Первый показывает оптимистичное обновление UI с откатом при ошибке, второй — что клиент хранит только код промокода, а суммы всегда приходят с сервера. Сравните с `context/CartContext.tsx`: корзина живёт в `localStorage`, избранное — в MySQL, отсюда и разная стратегия.

## Известные ограничения / что дальше

- `users.verification_code` хранится в открытом виде (PIN короткий и живёт 24 ч, но по идее его стоит хэшировать, как пароль). Хэширование — отдельная задача: тогда нельзя будет показывать код в логе «как есть», и fallback для локальной разработки придётся переделать.
- Повторная отправка PIN не имеет rate-limit: защиту от «спам-повторов» и от перебора кода (например, 5 попыток на аккаунт) стоит добавить отдельно.
- `spring.jpa.hibernate.ddl-auto=update` удобен в разработке, но для боя нужны миграции (Flyway/Liquibase): Hibernate не изменит и не удалит колонки, которые уже существуют с другими правилами.
- Скинута только одна скидка (один промокод на заказ); суммировать несколько кодов и делать «скидка от категории» текущая модель `Order` (один процент-слепок) не умеет.
- Загруженность фаворитов: `GET /api/favorites/ids` отдаёт все id без пагинации — при десятках тысяч избранных понадобится постраничность.
