package com.example.groupinterpreter.voice

/** Only the model's plaintext transcript leaves JNI. No recording is uploaded. */
internal object WhisperBridge {
    init { System.loadLibrary("voice_whisper") }
    external fun load(modelPath: String): Long
    external fun transcribe(context: Long, samples: FloatArray, language: String): String
    external fun unload(context: Long)
}
