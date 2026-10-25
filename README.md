# OpenCode mobile

Android-приложение, внутри которого живёт **свой сервер OpenCode**: Ubuntu 24.04 в proot + `opencode serve`
на 127.0.0.1. Компьютер не нужен. Интерфейс повторяет мобильный Claude.

## Как устроено
- `runtime/` — proot (из Termux, лежит в `jniLibs` как `libproot.so`), загрузка и распаковка rootfs и OpenCode,
  `ServerService` (foreground) держит процесс.
- `api/` — REST + SSE клиент OpenCode 1.x (`/session`, `/global/event`, `/config`, `/auth/*` …).
- `data/` — состояние чатов из SSE, каталог моделей, **учёт лимитов** (5ч / 7д / 30д по cost ответов).
- `ui/` — Compose-экраны: главная, чат (Markdown, инструменты, diff, картинки с зумом), Recents, Projects,
  Usage, Providers, Models, Permissions, Connectors (MCP), Server, Config, Profile, выбор иконки.
- Иконки: 11 вариантов из пака, переключаются через `activity-alias` (`ui/theme/AppIcons.kt`).

## Версии
OpenCode запинен на 1.19.4 (`RuntimeController.OC_VERSION`) — клиент проверен на его API.
`targetSdk = 28` осознанно: proot запускает бинарники из каталога данных (как Termux).

## Сборка
```
export JAVA_HOME=~/toolchain/jdk
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon
```
Перед сборкой проверить свободную память (`free -m`). APK: `app/build/outputs/apk/debug/`.

## Не проверено на устройстве
Запуск proot/OpenCode на телефоне, foreground-сервис под Android 14–16, вход по OAuth, камера и
сохранение картинок. Юнит-тесты разбирают реальные ответы сервера, но сам UI не запускался.
