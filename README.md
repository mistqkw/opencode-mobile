# OpenCode mobile

<p align="center"><img src="docs/banner.png" alt="OpenCode mobile"></p>

**v0.0.1 beta-1** · Android 8+ (arm64) · неофициальный клиент

Android-приложение, внутри которого живёт **свой сервер OpenCode**: Ubuntu 24.04 в proot + `opencode serve`
на 127.0.0.1. Компьютер не нужен. Можно и наоборот — подключиться к OpenCode на компьютере (Code → Devices).

<p align="center">
  <img src="docs/screenshots/claude-home.png" width="22%" alt="Claude">
  <img src="docs/screenshots/terminal-home.png" width="22%" alt="OpenCode Terminal">
  <img src="docs/screenshots/desktop-home.png" width="22%" alt="OpenCode Desktop">
  <img src="docs/screenshots/memory.png" width="22%" alt="Memory">
</p>

## Что умеет
- **Три интерфейса** (Settings → Interface): *Claude* — мягкий, как мобильный Claude; *OpenCode · Terminal* —
  моноширинный, прямые углы, синяя полоса у сообщений, как в консоли; *OpenCode · Desktop* — плоские панели,
  Inter, бледно-синий акцент, как в десктоп-приложении. Окна выбора модели и «Add to session» тоже свои в каждом.
- **Чат и Code раздельно.** Обычные чаты живут в `/root/chats`, всё остальное — вкладка Code с проектами.
- **Память.** Агент читает `/root/memory/MEMORY.md` и раскладывает факты по «полкам» (about-me, work, projects…).
  Правила памяти и ручные заметки — Settings → Memory.
- **Лимиты и расход** (5 ч / 7 д / 30 д), картинки в чате, Scheduled, Artifacts, MCP-коннекторы, навыки, права.
- **Вход не пропадает:** копия ключей и памяти лежит вне окружения и переживает его переустановку.
- Пасхалки: нажмите на надпись «opencode» на новом чате.

<p align="center">
  <img src="docs/screenshots/claude-models.png" width="22%" alt="Select model">
  <img src="docs/screenshots/claude-add.png" width="22%" alt="Add to session">
  <img src="docs/screenshots/desktop-models.png" width="22%" alt="Desktop picker">
  <img src="docs/screenshots/terminal-models.png" width="22%" alt="Terminal picker">
</p>

## Установка
1. Скачайте APK из [Releases](../../releases) и установите (разрешите установку из неизвестных источников).
2. При первом запуске приложение скачает и распакует окружение (Ubuntu Base + OpenCode) — нужен интернет и место.
3. Выберите модель. Бесплатные модели Zen работают без подписки, но у них общий дневной лимит на сеть (IP);
   в Settings → Providers можно подключить GitHub Copilot, Google Gemini, Groq, OpenRouter или свой ключ.

Бета: возможны ошибки. Сообщения об ошибках — в [Issues](../../issues).

## Как устроено
- `runtime/` — proot (из Termux, лежит в `jniLibs` как `libproot.so`), загрузка и распаковка rootfs и OpenCode,
  `ServerService` (foreground) держит процесс.
- `api/` — REST + SSE клиент OpenCode 1.x (`/session`, `/global/event`, `/config`, `/auth/*` …).
- `data/` — состояние чатов из SSE, каталог моделей, учёт лимитов, память (`Memory`), хранилище входа (`Vault`).
- `ui/` — Compose-экраны и компоненты; редакции интерфейса — `ui/theme/Theme.kt` (`Oc.round`, `Oc.pill`, палитры).
- `tools/promo/` — скрипты, которыми сделаны ролик и баннер.

## Версии
OpenCode запинен на 1.19.4 (`RuntimeController.OC_VERSION`) — клиент проверен на его API.
`targetSdk = 28` осознанно: proot запускает бинарники из каталога данных (как Termux).

## Сборка
```
export JAVA_HOME=~/toolchain/jdk
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon
```
Перед сборкой проверьте свободную память (`free -m`). APK: `app/build/outputs/apk/debug/`.

## Что не проверено
Вход через GitHub Copilot после ввода кода и ключи Google/Groq/OpenRouter на разных аккаунтах,
Auto Backup (восстановление после переустановки), светлые темы редакций, Android 14–16 в разных прошивках.

## Лицензии и оговорка
Неофициальный клиент, не связан с разработчиками OpenCode. «OpenCode» — название их проекта (MIT).
Приложение содержит proot и libtalloc из пакетов Termux (GPL-2.0 / LGPL-3.0), исходники — в проекте Termux;
при первом запуске скачивает Ubuntu Base 24.04 и OpenCode. Шрифт Inter — SIL OFL 1.1.
У самого приложения пока нет файла лицензии: все права сохранены за автором, пока не указано иное.
