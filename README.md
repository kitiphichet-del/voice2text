# ล่ามไทย–จีน รุ่นง่าย V2.0.0

Android app: **พูดไทย → แปลจีน** / **说中文 → แปลไทย**. Press a single button, say one sentence, pause, read its translated text. No voice output, no speaker detection, no continuous-recording queue or large Whisper model.

## How it works
- **Speech:** Uses **only** Android 12+ `SpeechRecognizer.createOnDeviceSpeechRecognizer()`. It never falls back to `createSpeechRecognizer()` (which may use online speech services). Requires the device's on-device speech engine and installed Thai / Chinese speech packs. A recognizer may be on-device but not support both languages.
- Only the final speech result is translated; partial results are shown as small visual feedback to avoid translating unstable prefixes.
- **Translation:** ML Kit device model, downloaded explicitly over Wi-Fi first. Voice recognition and text translation don't use a cloud API after required models are installed.
- No microphone file saving or output speech. Backgrounding stops recognition; rotation retains the ViewModel.
- Text fallback is available on Android 8+ and when voice is not supported by the system.
- Spoken input is **one utterance per tap**, not a continuous meeting/word-by-word interpreter. This avoids the previous delay and repeating syllables from the Whisper Tiny model.
- If speech confidence is inadequate, it may still recognize incorrect words; the original recognized sentence is shown below the output to help detect that.
- Google ML Kit may translate TH↔ZH via English and cannot guarantee Simplified Chinese or idiomatic formal meeting language.

## First use
1. Install APK. Tap **ดาวน์โหลดโมเดลแปลภาษา (Wi-Fi) — ครั้งแรก**.
2. Grant microphone permission; tap one language button and speak naturally.
3. An **on-device speech recognition provider** must be installed and support Thai and/or Mandarin. For Android 13+ if a language is unavailable, the app asks Android to download its model; if Android can't install it, configure language packs through your system speech settings.
4. To test offline, enable airplane mode after speech and translation language packs are installed.
5. If the device cannot recognize offline, the app gives a reason and keeps the text translation function.

## Build
https://github.com/kitiphichet-del/voice2text/actions/workflows/build-apk.yml

## Technical references
- Android SpeechRecognizer: https://developer.android.com/reference/android/speech/SpeechRecognizer
- On-device SpeechRecognizer: https://developer.android.com/reference/android/speech/SpeechRecognizer#createOnDeviceSpeechRecognizer(android.content.Context)
- ML Kit on-device translation: https://developers.google.com/ml-kit/language/translation/android

The APK build and unit tests run in GitHub Actions. Runtime compatibility and translation accuracy still require physical-device testing.
