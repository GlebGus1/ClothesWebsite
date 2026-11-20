#!/usr/bin/env bash
# Останавливает бэкенд: снимаем и JVM, слушающую порт 8080, и процесс Maven, если он ещё жив.
cd "$(dirname "$0")/.." || exit 1
PORT_PID=$(netstat -ano 2>/dev/null | grep LISTENING | grep ":8080 " | awk '{print $NF}' | head -1)
if [ -n "${PORT_PID:-}" ]; then
  taskkill //PID "$PORT_PID" //F >/dev/null 2>&1 && echo "Остановлен процесс $PORT_PID (порт 8080)"
fi
if [ -f target/backend-mvn.pid ]; then
  taskkill //PID "$(cat target/backend-mvn.pid)" //T //F >/dev/null 2>&1 && echo "Остановлен Maven $(cat target/backend-mvn.pid)"
  rm -f target/backend-mvn.pid
fi
sleep 2
netstat -ano 2>/dev/null | grep LISTENING | grep ":8080 " || echo "Порт 8080 свободен"
