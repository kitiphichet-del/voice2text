# Thai–Chinese Group Interpreter / ล่ามไทย–จีน V 1.1.0

Offline-first Android text + microphone translation app: Thai ↔ Chinese.
**All speech-to-text inference and translation run on the Android phone.** There is no cloud speech-recognition API, no upload of microphone audio, no speech playback, and no speaker identification.

## Features

- **Text mode:** type or paste Thai / Chinese, automatic or manual translation direction; only most recent translation shown.
- **Voice mode:** tap Start, Android microphone records mono 16 kHz audio, process in 7-second chunks using **Whisper.cpp v1.9.4** (native JNI, Android NDK/CMake), route Thai/Chinese transcription to **ML Kit on-device translation**. Press Stop to process final audio segment.
- Voice language: Auto / force Thai / force Chinese. Use forced language when automatic recognition struggles with short utterances or switching speakers.
- Pausing isn't implemented. Changing orientation retains the ViewModel and active recording. Leaving the app / screen off stops capture; this build **does not** include background recording or a foreground service.
- No saved audio files or past transcript list. Buffering and transcription operate only in memory. UI displays last recognized segment and last translation.
- Bounded ASR queue with a warning if phone CPU cannot keep up; slow phones may skip segments instead of losing control or exhausting memory.
- Strict offline voice translation: when a required translation model is missing, show an error instead of silently using the network.

## First launch / airplane mode

1. Use Wi-Fi to press **เตรียมโมเดลภาษาไทย–จีน** (ML Kit translation).
2. Switch to Voice mode, press **ดาวน์โหลดโมเดลเสียง Whisper (~60 MB)**. The multilingual model is downloaded from Hugging Face, the official converted Whisper model repository, and validated against a pinned SHA-256.
3. Grant the Android **RECORD_AUDIO** permission and tap **เริ่มฟัง**. No internet is needed after both model families have been installed and retained. Try Airplane Mode to verify this.
4. An initial download is required once on each device. A major reinstall, clearing app data, or model deletion may require a new download.

Speech model: `ggml-base-q5_1.bin` (multilingual model, **not** `base.en`); 59.7 MB, published by ggerganov/whisper.cpp; SHA-256:
`422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898`.
Download source:
https://huggingface.co/ggerganov/whisper.cpp/blob/f281eb45af861ab5e5297d23694b7d46e090c02c/ggml-base-q5_1.bin

## Build APK

GitHub Actions (build + unit tests): https://github.com/kitiphichet-del/voice2text/actions/workflows/build-apk.yml

The CI runner needs Android SDK API 36, NDK 27.2.12479018, CMake 3.22.1, Gradle 8.13 and JDK 17.
The first **developer build** downloads pinned whisper.cpp sources using CMake FetchContent from:
https://github.com/ggml-org/whisper.cpp/tree/v1.9.4.
**Runtime offline speech does not depend on the build-time source retrieval.**

Source project can also be opened in Android Studio with these SDK tools installed; the first Gradle sync/CMake configuration needs development-machine internet.

## Technical limitations

- Chunked near-real-time, **not true simultaneous word-level interpreting**. Processing latency depends on the device CPU and speech duration. Longer speech or heavy background usage can cause dropped chunks, indicated onscreen.
- Whisper multilingual base is not perfect for Thai or Mandarin, overlapping voices, or code-switching in one window. Unclear results require correction. No speaker diarization.
- Chinese simplified-only output **cannot be guaranteed** by the current text translation model.
- ML Kit may route Thai ↔ Chinese via English and is designed primarily for casual/basic translations; verify nuance before formal meetings or important decisions.
- No cloud AI, no automatic speech output, no audio streaming, and no ongoing background listening.
- Microphone is intentionally stopped when the activity backgrounds; keeping capture running with screen off would require a dedicated microphone Foreground Service.
- Model assets are downloaded **by the app once**, not bundled into this APK.
- Device microphone, Android permission, download connection, CPU performance, and offline usability require **real-device testing** after a successful CI build.

## Third-party software

- whisper.cpp (MIT): https://github.com/ggml-org/whisper.cpp
- Whisper GGML model (MIT license on model card): https://huggingface.co/ggerganov/whisper.cpp
- ML Kit on-device translation: https://developers.google.com/ml-kit/language/translation

Check Google's current ML Kit translation attribution/branding requirements before release to an app store.
