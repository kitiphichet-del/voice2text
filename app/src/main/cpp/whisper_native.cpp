#include <jni.h>
#include <android/log.h>
#include <whisper.h>
#include <algorithm>
#include <string>
#include <thread>

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "InterpreterWhisper", __VA_ARGS__)

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_groupinterpreter_voice_WhisperBridge_load(
        JNIEnv *env, jobject, jstring modelPath) {
    if (!modelPath) return 0;
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    if (!path) return 0;
    whisper_context_params contextParams = whisper_context_default_params();
    whisper_context *ctx = whisper_init_from_file_with_params(path, contextParams);
    env->ReleaseStringUTFChars(modelPath, path);
    if (!ctx) LOGE("Could not open Whisper model");
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_groupinterpreter_voice_WhisperBridge_transcribe(
        JNIEnv *env, jobject, jlong nativeContext, jfloatArray samples,
        jstring preferredLanguage) {
    auto *ctx = reinterpret_cast<whisper_context *>(nativeContext);
    if (!ctx || !samples || !preferredLanguage) return env->NewStringUTF("");
    const jsize count = env->GetArrayLength(samples);
    if (count < WHISPER_SAMPLE_RATE / 2) return env->NewStringUTF("");
    jfloat *pcm = env->GetFloatArrayElements(samples, nullptr);
    if (!pcm) return env->NewStringUTF("");
    const char *lang = env->GetStringUTFChars(preferredLanguage, nullptr);
    if (!lang) {
        env->ReleaseFloatArrayElements(samples, pcm, JNI_ABORT);
        return env->NewStringUTF("");
    }

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = std::min(4u, std::max(2u, std::thread::hardware_concurrency()));
    params.language = lang;
    params.translate = false;
    params.no_context = true;
    params.no_timestamps = true;
    params.single_segment = true;
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.suppress_nst = true;
    params.suppress_blank = true;
    params.temperature_inc = 0.0f; // Skip costly temperature retries on slow devices.
    params.max_tokens = 96;        // Bound a pathological hallucinated decode.
    params.greedy.best_of = 1;

    const int outcome = whisper_full(ctx, params, pcm, count);
    std::string output;
    if (outcome == 0) {
        for (int i = 0; i < whisper_full_n_segments(ctx); i++) {
            const char *segment = whisper_full_get_segment_text(ctx, i);
            if (segment) output += segment;
        }
    } else {
        LOGE("whisper_full failed with %d", outcome);
    }
    env->ReleaseStringUTFChars(preferredLanguage, lang);
    env->ReleaseFloatArrayElements(samples, pcm, JNI_ABORT);
    return env->NewStringUTF(output.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_groupinterpreter_voice_WhisperBridge_unload(
        JNIEnv *, jobject, jlong nativeContext) {
    auto *ctx = reinterpret_cast<whisper_context *>(nativeContext);
    if (ctx) whisper_free(ctx);
}
