# ล่ามไทย–จีน / Thai–Chinese Group Interpreter

**สถานะ: Android Studio source project / not a precompiled APK.**

แอปแปล **ข้อความ** ภาษาไทย ↔ ภาษาจีน โดยแสดงเฉพาะคำแปลล่าสุดในช่องผลลัพธ์ ไม่ตอบคำถามและไม่แสดงความคิดเห็น ไม่ต้องใช้ไมโครโฟน ไม่เก็บประวัติการสนทนา ชื่อผู้พูดก่อนเครื่องหมาย `:` หรือ `：` จะถูกคงไว้ตามที่ผู้พูดพิมพ์

## Features

- Auto direction: count Thai and Han script characters; if tied/absent fall back to ML Kit language ID. If still inconclusive, request user to pick an explicit direction.
- Thai → Chinese, Chinese → Thai manual direction override.
- Debounced automatic translation (850 ms) + explicit Translate button.
- Only latest input output shown: changes immediately clear previous result; request generation number rejects late results from old requests.
- ML Kit on-device translation. Download both language models via Wi-Fi button. Downloads require internet once; already-downloaded models can be used offline. For manual translate without predownload, ML Kit may download on mobile data: prefer using the Wi-Fi preparation button first.
- Speaker prefix up to 32 characters (letters / spaces / some punctuation, no digits) is preserved exactly and not translated. This doesn't identify speakers automatically.
- Select-to-copy and Copy button. ViewModel retains current state after screen rotation.
- Version and progress/error status at the bottom.
- No ads, login, voice, cloud chat, Firebase, or API keys.

## GitHub project

Repository: https://github.com/kitiphichet-del/voice2text

## Build APK

1. Install **Android Studio** with **Android SDK 36**, SDK Build Tools, and **JDK 17+**; internet is required to retrieve Gradle dependencies on the *development machine*.
2. Unzip this folder and open its root folder in Android Studio.
3. This archive includes `gradle/wrapper/gradle-wrapper.properties` but **does not include a Gradle wrapper JAR**. If Android Studio requires one, install Gradle 8.13 locally, run `gradle wrapper --gradle-version 8.13`, and then click **Sync Project with Gradle Files**.
4. Select **Build → Build Bundle(s) / APK(s) → Build APK(s)** or run `gradlew.bat assembleDebug` (Windows) / `./gradlew assembleDebug` (Linux/macOS) *after generating the Gradle wrapper*.
5. The expected file after a successful build is `app/build/outputs/apk/debug/app-debug.apk` (do not confuse with this uncompiled source archive).
6. **Build without Android Studio**: go to [GitHub Actions](https://github.com/kitiphichet-del/voice2text/actions/workflows/build-apk.yml), select **Build Thai-Chinese Android APK → Run workflow**. After a *successful* run, download the `ThaiChineseInterpreter-debug-apk` artifact and extract `app-debug.apk`. The workflow also runs Android JUnit unit tests before producing the APK.
7. Install to Android 8.0+ (API 26+) and tap **เตรียมโมเดลภาษาไทย–จีน (Wi-Fi)** before airplane-mode use.

## Known limitations / non-promises

- On-device ML Kit is a sentence translation model, **not an instruction-following LLM**. Therefore the app can enforce translation-only UI logic, but cannot guarantee polished professional context, nuanced honorifics, full consistency for speaker names, or exclusively Simplified characters in every result.
- Mixed Thai/Chinese is routed based on the predominant script, and mixed passages are translated as a whole; individual fragments may not all be transformed as intended. Choose manual direction when automatic mode is not confident.
- Only an explicitly typed leading speaker label is retained. Real diarization, transcription, multi-person voice, online meetings and TTS are **not in this text-only MVP**.
- The language model files are **not bundled** with this source ZIP; the first run requires download.
- Google ML Kit translates non-English pairs via English as an intermediary; review high-stakes or formal translations carefully.
- Before public publication, review [ML Kit attribution rules](https://developers.google.com/ml-kit/language/translation/translation-terms) and Google's official attribution assets; the UI currently displays a plain-text "Powered by Google Translate" notice, not the approved graphic. Ensure branding meets current published requirements.
- This project was assembled without a local Android SDK or Gradle distribution. Build status must be confirmed by a successful GitHub Actions run. Device testing must still be completed. Pure Kotlin routing tests and Android JUnit routing tests are included.

## Pure Kotlin tests

With Java 17+ and Kotlin compiler installed:

```bash
kotlinc app/src/main/java/com/example/groupinterpreter/core/MessageRules.kt \
    logic-tests/MessageRulesTest.kt -include-runtime -d logic-tests/tests.jar
java -jar logic-tests/tests.jar
```

## Verified references

- [ML Kit Translate Android guide](https://developers.google.com/ml-kit/language/translation/android) (`com.google.mlkit:translate:17.0.3`)
- [ML Kit Language Identification](https://developers.google.com/ml-kit/language/identification/android) (`com.google.mlkit:language-id:17.0.6`)
- [Supported translation languages](https://developers.google.com/ml-kit/language/translation/translation-language-support) (Thai and Chinese)
- [On-device translation limitations](https://developers.google.com/ml-kit/language/translation)

Copyright: original sample code created for this request. External libraries follow their respective licenses and attribution rules.
