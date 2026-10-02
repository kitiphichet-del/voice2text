# ล่ามไทย–จีน รุ่นง่าย V2.1.0

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


## V2.1.0: fix Chinese speech recognition preflight
- Mandarin voice still uses **zh-CN** spoken Mandarin, NOT Pinyin. Pinyin is romanization of Mandarin, e.g. "nǐ hǎo" corresponds to "你好".
- On Android 13+ (API 33), Chinese microphone starts with `checkRecognitionSupport` to find an **installed on-device Mandarin pack** separately from Thai. Honor installed locale variants `zh-CN`, `cmn-Hans-CN`, `zh-Hans-CN`, etc.
- If the device offers an offline Chinese language model but it is not installed, ask Android to download it (`triggerModelDownload`) and show instructions to connect Wi-Fi and retry. It may require approval in Android's system UI.
- If the Android recognizer reports no supported on-device Mandarin, report that explicitly; **never switch to an online recognizer without consent**.
- If the speech engine produces Latin/Pinyin instead of Chinese characters, display the recognized text and warn; do not feed Pinyin blindly into Chinese→Thai translation.
- Android 12 (API 31–32) cannot query a per-language support list through this API; speech is attempted using the on-device recognizer with normal language error feedback.
- APK compilation and unit tests in GitHub Actions are not a substitute for testing the target phone. A Chinese pack may not exist for every Android device/vendor.
