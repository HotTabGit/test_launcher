# Minecraft Voice (Android)

Нативный Android-клиент голосовой связи для комнаты `global`. Интерфейс и системная часть готовы для игры поверх Minecraft; транспорт пока реализован как `MockVoiceClient`.

## Структура

`app/src/main/java/ru/minecraftvoice/`: `data/preferences` (DataStore), `domain/model`, `presentation`, `voice`, `service`, `overlay`, `MainActivity`.

## Зависимости

Kotlin 2.0, Android Gradle Plugin 8.7, Jetpack Compose Material 3, Lifecycle Compose, DataStore Preferences и Kotlin Coroutines (через AndroidX). Минимальный Android — API 26; target SDK — 35.

`AndroidManifest.xml` объявляет `RECORD_AUDIO`, `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS` и `INTERNET`. Микрофон и Overlay запрашиваются только после понятного собственного объяснения; Overlay открывает системную страницу настроек Android.

## Запуск и APK

1. Откройте корневую папку в Android Studio (JDK 17, Android SDK 35), дождитесь Gradle Sync.
2. Выберите устройство с Android 8+ и нажмите **Run**.
3. Для debug APK выполните Windows-команду `gradlew.bat assembleDebug` (macOS/Linux: `./gradlew assembleDebug`). Итог: `app/build/outputs/apk/debug/app-debug.apk`.

После входа выдайте последовательно доступ к микрофону и разрешение показывать поверх других приложений. Нажатие «ВКЛЮЧИТЬ ОВЕРЛЕЙ» запускает foreground service, показывает постоянное уведомление, создаёт draggable Overlay и сворачивает приложение.

## LiveKit позднее

Добавьте LiveKit Android SDK и создайте `LiveKitVoiceClient`, реализующий интерфейс `voice/VoiceClient`. Клиент должен сначала обратиться по HTTPS к вашему Rust API за короткоживущим токеном, затем подключиться к `global`. URL сервера и токен передавайте извне APK: секрет API/LiveKit key никогда не добавляются в приложение. Замените `MockVoiceClient()` в `VoiceForegroundService` на внедрённую реализацию LiveKit.
