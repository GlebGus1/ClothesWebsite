#!/usr/bin/env bash
# Смоук-тест REST API (18 проверок: гость / USER / ADMIN, JWT, refresh, права, загрузка файлов).
# Требуется запущенный бэкенд на http://localhost:8080:
#   bash scripts/mvn.sh spring-boot:run -Dspring-boot.run.arguments="--app.admin.email=admin@clothes.shop --app.admin.password=SuperSecret123"
#   bash scripts/smoke-test.sh
set -u
API="${API:-http://localhost:8080}"
# Временная папка внутри target/ (она в .gitignore): Windows-версия node не понимает /tmp
TMP="$(cd "$(dirname "$0")/.." && pwd)/target/smoke-$(date +%s)"
mkdir -p "$TMP"
STAMP="$(date +%s)"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@clothes.shop}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-SuperSecret123}"
USER_EMAIL="user${STAMP}@example.com"
USER_PASSWORD="UserPass123"

ok()   { printf "  \033[32mOK\033[0m   %s\n" "$1" >&2; }
fail() { printf "  \033[31mFAIL\033[0m %s\n" "$1" >&2; FAILED=1; }
note() { printf "  ->  %s\n" "$1" >&2; }

# $1 = ожидаемый HTTP-код, остальные = аргументы curl
check() {
  local expected="$1"; shift
  local body; body=$(curl -s -o "$TMP/out" -w "%{http_code}" "$@")
  if [ "$body" = "$expected" ]; then ok "HTTP $body"; else
    fail "ожидался $expected, получен $body"; head -c 400 "$TMP/out" >&2; echo >&2
  fi
  cat "$TMP/out"
}

# Достаёт поле из JSON-ответа: json <файл> <поле> или json <файл> items.length
json() { node -e "const fs=require('fs');const o=JSON.parse(fs.readFileSync(process.argv[1],'utf8'));const v=process.argv[2].split('.').reduce((a,k)=>a==null?a:a[k],o);console.log(v==null?'':v)" "$1" "$2"; }

FAILED=0
echo "== 1. Гость видит каталог =="
check 200 "$API/api/tshirts" > "$TMP/catalog"
note "всего товаров: $(json "$TMP/catalog" totalElements)"

echo "== 2. Гость не может оформить заказ / посмотреть свои заказы =="
check 401 "$API/api/orders/me" >/dev/null

echo "== 3. Регистрация нового пользователя =="
check 201 -X POST "$API/api/auth/register" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$USER_EMAIL\",\"password\":\"$USER_PASSWORD\",\"firstName\":\"Test\",\"lastName\":\"User\"}" > "$TMP/reg"
USER_ACCESS=$(json "$TMP/reg" accessToken); USER_REFRESH=$(json "$TMP/reg" refreshToken)
note "access длиною в $(json "$TMP/reg" expiresIn) сек, tokenType=$(json "$TMP/reg" tokenType)"

echo "== 4. Повторная регистрация того же e-mail -> 409 =="
check 409 -X POST "$API/api/auth/register" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$USER_EMAIL\",\"password\":\"$USER_PASSWORD\",\"firstName\":\"Test\",\"lastName\":\"User\"}" >/dev/null

echo "== 5. Неверный пароль -> 401, валидация e-mail -> 400 =="
check 401 -X POST "$API/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$USER_EMAIL\",\"password\":\"wrong\"}" >/dev/null
check 400 -X POST "$API/api/auth/login" -H 'Content-Type: application/json' -d '{"email":"invalid-email","password":"x"}' >/dev/null

echo "== 6. /api/auth/me по access-токену =="
check 200 "$API/api/auth/me" -H "Authorization: Bearer $USER_ACCESS" >/dev/null

echo "== 7. Refresh: старая пара отзывается, новая работает =="
check 200 -X POST "$API/api/auth/refresh" -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$USER_REFRESH\"}" > "$TMP/refresh"
NEW_REFRESH=$(json "$TMP/refresh" refreshToken); NEW_ACCESS=$(json "$TMP/refresh" accessToken)
check 401 -X POST "$API/api/auth/refresh" -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$USER_REFRESH\"}" >/dev/null
note "повторное использование старого refresh -> 401 (ротация работает)"
USER_ACCESS="$NEW_ACCESS"; USER_REFRESH="$NEW_REFRESH"

echo "== 8. Refresh-токен нельзя подставить вместо access =="
check 401 "$API/api/auth/me" -H "Authorization: Bearer $USER_REFRESH" >/dev/null

echo "== 9. USER не может создавать товары (403) =="
check 403 -X POST "$API/api/tshirts" -H "Authorization: Bearer $USER_ACCESS" \
  -F 'data={"name":"Hacker shirt","size":"M","price":100};type=application/json' >/dev/null

echo "== 10. Логин администратора =="
check 200 -X POST "$API/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" > "$TMP/admin"
ADMIN_ACCESS=$(json "$TMP/admin" accessToken); ADMIN_REFRESH=$(json "$TMP/admin" refreshToken)
note "роли админа: $(json "$TMP/admin" user)"

echo "== 11. ADMIN создаёт товар с картинкой (multipart) =="
node -e "require('fs').writeFileSync(require('path').resolve(process.argv[1]), Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==','base64'))" "$(cygpath -w "$TMP/shirt.png" 2>/dev/null || echo "$TMP/shirt.png")"
check 201 -X POST "$API/api/tshirts" -H "Authorization: Bearer $ADMIN_ACCESS" \
  -F "data={\"name\":\"Smoke T-Shirt $STAMP\",\"description\":\"smoke test product\",\"size\":\"M\",\"price\":799.5,\"available\":true};type=application/json" \
  -F "files=@$(cygpath -w "$TMP/shirt.png" 2>/dev/null || echo "$TMP/shirt.png");type=image/png" > "$TMP/created"
SHIRT_ID=$(json "$TMP/created" id)
IMAGE_ID=$(json "$TMP/created" images.0.id)
note "товар id=$SHIRT_ID, картинка id=$IMAGE_ID"

echo "== 12. Карточка и картинка доступны гостю =="
check 200 "$API/api/tshirts/$SHIRT_ID" >/dev/null
check 200 "$API/api/images/$IMAGE_ID" >/dev/null
check 200 "$API/api/tshirts?size=M&sort=price_asc&pageSize=5" >/dev/null
check 400 -X POST "$API/api/tshirts" -H "Authorization: Bearer $ADMIN_ACCESS" \
  -F "data={\"name\":\"\",\"size\":\"M\",\"price\":10};type=application/json" >/dev/null

echo "== 12b. Подделка картинки (EXE-байты с MIME image/png) отклоняется =="
printf 'MZ\x90\x00' > "$TMP/fake.png"
node -e "require('fs').appendFileSync(require('path').resolve(process.argv[1]), Buffer.alloc(64,1))" "$(cygpath -w "$TMP/fake.png" 2>/dev/null || echo "$TMP/fake.png")"
check 400 -X POST "$API/api/tshirts" -H "Authorization: Bearer $ADMIN_ACCESS" \
  -F 'data={"name":"Fake image","description":"x","size":"M","price":100,"available":true};type=application/json' \
  -F "files=@$(cygpath -w "$TMP/fake.png" 2>/dev/null || echo "$TMP/fake.png");type=image/png" >/dev/null
note "проверка по содержимому (магические числа) работает"

echo "== 13. USER оформляет заказ =="
check 201 -X POST "$API/api/orders" -H "Authorization: Bearer $USER_ACCESS" -H 'Content-Type: application/json' \
  -d "{\"nameOfUser\":\"Test User\",\"postOffice\":\"Post office 12\",\"postalCode\":\"01001\",\"phoneNumber\":\"+380631234567\",\"tshirtIds\":[$SHIRT_ID]}" > "$TMP/order"
ORDER_ID=$(json "$TMP/order" id)
note "заказ #${ORDER_ID} статус=$(json "$TMP/order" status) позиций=$(json "$TMP/order" items.length)"

echo "== 14. Заказ с несуществующим товаром -> 404, пустая корзина -> 400 =="
check 404 -X POST "$API/api/orders" -H "Authorization: Bearer $USER_ACCESS" -H 'Content-Type: application/json' \
  -d "{\"nameOfUser\":\"Test User\",\"postOffice\":\"Post office 12\",\"postalCode\":\"01001\",\"phoneNumber\":\"+380631234567\",\"tshirtIds\":[999999]}" >/dev/null
check 400 -X POST "$API/api/orders" -H "Authorization: Bearer $USER_ACCESS" -H 'Content-Type: application/json' \
  -d "{\"nameOfUser\":\"Test User\",\"postOffice\":\"Post office 12\",\"postalCode\":\"01001\",\"phoneNumber\":\"+380631234567\",\"tshirtIds\":[]}" >/dev/null

echo "== 15. USER видит свои заказы, но не чужие =="
check 200 "$API/api/orders/me" -H "Authorization: Bearer $USER_ACCESS" >/dev/null
check 403 "$API/api/admin/orders" -H "Authorization: Bearer $USER_ACCESS" >/dev/null
check 403 -X PATCH "$API/api/admin/orders/$ORDER_ID/status" -H "Authorization: Bearer $USER_ACCESS" \
  -H 'Content-Type: application/json' -d '{"status":"DELIVERED"}' >/dev/null

echo "== 16. ADMIN видит все заказы и меняет статус =="
check 200 "$API/api/admin/orders" -H "Authorization: Bearer $ADMIN_ACCESS" >/dev/null
check 200 -X PATCH "$API/api/admin/orders/$ORDER_ID/status" -H "Authorization: Bearer $ADMIN_ACCESS" \
  -H 'Content-Type: application/json' -d '{"status":"IN_TRANSIT"}' >/dev/null
check 200 "$API/api/admin/users" -H "Authorization: Bearer $ADMIN_ACCESS" >/dev/null

echo "== 17. Logout отзывает refresh-токен =="
check 204 -X POST "$API/api/auth/logout" -H "Authorization: Bearer $USER_ACCESS" \
  -H 'Content-Type: application/json' -d "{\"refreshToken\":\"$USER_REFRESH\"}" >/dev/null
check 401 -X POST "$API/api/auth/refresh" -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$USER_REFRESH\"}" >/dev/null

echo "== 18. ADMIN выключает товар (мягкое удаление) =="
check 204 -X DELETE "$API/api/tshirts/$SHIRT_ID" -H "Authorization: Bearer $ADMIN_ACCESS" >/dev/null
check 404 "$API/api/tshirts/$SHIRT_ID" >/dev/null
check 404 -X DELETE "$API/api/tshirts/$SHIRT_ID" -H "Authorization: Bearer $ADMIN_ACCESS" >/dev/null

rm -rf "$TMP"
echo
if [ "$FAILED" = "0" ]; then echo "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ"; else echo "ЕСТЬ ПАДЕНИЯ — смотри выше"; fi
exit $FAILED
