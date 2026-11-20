# _legacy_thymeleaf — старое серверное HTML-представление (Thymeleaf + HttpSession)

Сюда перенесён старый интерфейс **без удаления** (в проекте нет Git-истории, поэтому
код сохраняется, а не удаляется). Эти файлы **не компилируются** и не участвуют в сборке:
`src/main/java`, `src/main/resources/templates`, `src/main/resources/static` их больше не содержат.

| Папка | Что было раньше |
|---|---|
| `controllers/` | `src/main/java/com/example/appShop/сontrollers/*` (обратите внимание: имя пакета было написано с **кириллической** `с`) |
| `configs/` | `src/main/java/com/example/appShop/сonfigs/*` (`SecurityConfig` с `formLogin`, `WebConfig` с ViewController) |
| `templates/` | `src/main/resources/templates/*.html` (tshirts, cart, orders, login, register, profile, …) |
| `static/` | `src/main/resources/static/styles/**` (CSS/JS/картинки старого UI) |

Полноценная замена этому интерфейсу — SPA в папке `frontend/` (React + Vite + React Router + Axios),
она общается с бэкендом только через JSON REST API (`/api/**`) и JWT-токены.

Если понадобится вернуться к старому UI: верните папки на места и добавьте в `pom.xml`
`spring-boot-starter-thymeleaf` + `thymeleaf-extras-springsecurity6`.
