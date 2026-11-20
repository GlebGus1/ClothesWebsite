#!/usr/bin/env bash
# Запуск бэкенда в фоне: лог в target/backend.log, PID в target/backend.pid.
# Параметры первого администратора можно переопределить переменными окружения.
cd "$(dirname "$0")/.." || exit 1
mkdir -p target
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@clothes.shop}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-SuperSecret123}"
bash scripts/mvn.sh spring-boot:run \
  -Dspring-boot.run.arguments="--app.admin.email=$ADMIN_EMAIL --app.admin.password=$ADMIN_PASSWORD" \
  > target/backend.log 2>&1 &
echo $! > target/backend-mvn.pid
echo "Запускаю бэкенд (лог: target/backend.log). Жду старт..."
for i in $(seq 1 60); do
  if grep -q "Started AppShopRun" target/backend.log 2>/dev/null; then
    echo "Бэкенд запущен: http://localhost:8080"
    exit 0
  fi
  if grep -q "APPLICATION FAILED TO START\|BUILD FAILURE" target/backend.log 2>/dev/null; then
    echo "Старт не удался, последние строки лога:"
    tail -30 target/backend.log
    exit 1
  fi
  sleep 1
done
echo "Таймаут ожидания. Смотри target/backend.log"
exit 1
