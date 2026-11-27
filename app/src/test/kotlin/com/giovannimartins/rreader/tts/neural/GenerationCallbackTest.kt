package com.giovannimartins.rreader.tts.neural

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The contract sherpa-onnx's native code relies on: it calls `invoke(float[])` returning `java.lang.Integer` by
 * name. A Kotlin lambda in its place crashed the app on the phone (JNI NoSuchMethodError, then SIGABRT).
 */
class GenerationCallbackTest {
    @Test
    fun `has the invoke method the native code looks up`() {
        val invoke = GenerationCallback::class.java.getMethod("invoke", FloatArray::class.java)

        assertThat(invoke.returnType).isEqualTo(Integer::class.java)
    }

    @Test
    fun `goes on while asked to and stops when told`() {
        var keepGoing = true
        val callback = GenerationCallback { keepGoing }

        assertThat(callback(floatArrayOf(0f))).isEqualTo(GenerationCallback.CONTINUE)
        keepGoing = false
        assertThat(callback(floatArrayOf(0f))).isEqualTo(GenerationCallback.STOP)
    }
}
