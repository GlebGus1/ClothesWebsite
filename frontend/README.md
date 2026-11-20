# Фронтенд — React + TypeScript + Vite

SPA, которая полностью заменила старые Thymeleaf-шаблоны (они в `_legacy_thymeleaf/`).

## Команды

```bash
npm install       # установка зависимостей
npm run dev       # dev-сервер на http://localhost:5173 (proxy /api -> :8080)
npm run build     # tsc --noEmit + vite build -> dist/
npm run preview   # локальный показ production-сборки
npm run typecheck # только проверка типов
```

Нужен запущенный бэкенд на `http://localhost:8080` (`bash scripts/run-backend.sh` из корня проекта).

## Структура

```
src/
  api/
    http.ts       # axios-инстанс: Bearer-заголовок, ротация refresh при 401, ApiRequestError
    auth.ts       # /api/auth/login | register | me
    catalog.ts    # /api/tshirts…: список, карточка, multipart-сохранение, превью, удаление
    orders.ts     # /api/orders…
    admin.ts      # /api/admin/…: заказы, статусы, пользователи
  auth/
    tokenStorage.ts  # где лежат токены в браузере (localStorage) + как их стирать
  context/
    AuthContext.tsx  # пользователь, login/register/logout, восстановление сессии
    CartContext.tsx  # корзина (localStorage) — множество id товаров без количества
  components/        # Header, Layout, ProductCard, Pagination, Spinner, ApiErrorBox, ProtectedRoute
  pages/             # Catalog, Product, Login, Register, Cart, Checkout, Orders, OrderDetails, NotFound
  pages/admin/       # AdminProducts, AdminProductForm, AdminOrders, AdminUsers
  styles/            # tokens.css (переменные), layout.css (каркас), components.css и pages.css
  types/api.ts       # TS-типы = Java-записи (DTO) бэкенда
  utils/format.ts    # деньги, даты, подписи статусов заказа
```

## Как ведёт себя авторизация (самое важное для понимания)

1. Логин/регистрация → бэкенд возвращает пару токенов, мы кладём их в `localStorage`
   (`auth/tokenStorage.ts`).
2. Каждый запрос через axios получает `Authorization: Bearer <access>` автоматически
   (request-интерцептор в `api/http.ts`) — страницы про токен ничего не знают.
3. Если Access-токен протух, бэкенд отвечает `401`. response-интерцептор один раз вызывает
   `/api/auth/refresh`, получает новую пару токенов и **повторяет исходный запрос**.
   Пользователь этого не замечает.
4. Если refresh не принял (отозван/истёк) → токены стираются, `AuthContext` получает
   `onSessionLost` и уводит на `/login`.
5. Открытие страницы: если в `localStorage` есть refresh-токен, делаем `GET /api/auth/me`
   (при необходимости он будет повторён после refresh) — сессия восстанавливается сама.

## Что специально НЕ сделано «во фронтенде»

- Правила валидации не дублируются: тексты ошибок приходят в `ApiError.fields` с бэкенда
  (аннотации Bean Validation в DTO). `pattern`/`minLength` в HTML — только подсказка пользователю.
- Итог заказа считается на сервере по ценам из БД — локальные цены в корзине только для отображения.
- `ProtectedRoute` — это удобство, а не защита. Защита — в `SecurityConfig` и `JwtAuthenticationFilter`.

## Переменные окружения

`.env.example` → `.env.local`:

- `VITE_API_URL` — базовый URL API. В dev можно оставить `/api` (работает прокси Vite).
  Для продакшн-сборки укажите полный адрес, например `https://api.example.com/api`.
