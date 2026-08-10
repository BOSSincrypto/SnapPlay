# SnapPlay

Быстрый минимальный видеоплеер для Android 9+.

## Возможности

- локальные видео через системный выбор файлов без доступа ко всей медиатеке;
- прямые HTTP/HTTPS-ссылки;
- глобальная скорость 0,25×–3×, сохранённая через DataStore;
- Picture-in-Picture с автоматическим входом на Android 12+;
- `SurfaceView` и аппаратные декодеры Media3;
- seek к ближайшему ключевому кадру для быстрого отклика;
- тёмный интерфейс без тяжёлой навигации и лишних экранов.

## Производительность

SnapPlay сокращает задержки, которыми управляет приложение. Он не может гарантировать мгновенную перемотку любого файла: точная скорость зависит от частоты ключевых кадров, кодека, накопителя, сети и декодера устройства.

Для быстрой перемотки исходные видео должны иметь частые ключевые кадры. Плеер использует `SeekParameters.CLOSEST_SYNC`, поэтому отдаёт приоритет отклику перед покадровой точностью seek.

## Сборка

Нужны JDK 17 и Android SDK 36.

```bash
./gradlew test lint assembleDebug
```

Debug APK появится в `app/build/outputs/apk/debug/`.

## CI и релизы

Pull request и push в `main` запускают тесты, lint и debug-сборку. APK сохраняется как GitHub Actions artifact на 14 дней.

Тег формата `vX.Y.Z` запускает подписанную APK/AAB-сборку и создаёт GitHub Release. Перед первым тегом создайте GitHub Environment `production` и secrets:

- `SNAPPLAY_KEYSTORE_BASE64` — upload-keystore в Base64 без переносов;
- `SNAPPLAY_STORE_PASSWORD`;
- `SNAPPLAY_KEY_ALIAS`;
- `SNAPPLAY_KEY_PASSWORD`.

Пример кодирования keystore:

```bash
base64 -w 0 snapplay-upload.jks
```

Защитите теги `v*` и environment `production`. Для Google Play включите Play App Signing; в GitHub храните только upload key.

## Стек на 10 августа 2026

AGP 9.3.1, Gradle 9.5.0, JDK 17, Kotlin/Compose plugin 2.3.21, compile/target SDK 36, Compose BOM 2026.06.01, Media3 1.11.0.

## Лицензия

Apache-2.0.
