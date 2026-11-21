package com.chrishonson.gemmavoice.bench

import android.content.Context
import android.os.Build
import com.chrishonson.gemmavoice.llm.LlmBackend
import java.io.File

/** Writes reports to bench/ under the app's external files directory, beside the adb benchmark's. */
class AndroidBenchmarkHost(context: Context, backend: LlmBackend) : BenchmarkHost {
    private val baseDir = context.getExternalFilesDir(null) ?: context.filesDir

    override val deviceModel: String = Build.MODEL
    override val backend: String = backend.name.lowercase()

    override fun writeReport(name: String, json: String): String {
        val file = File(baseDir, "bench/$name")
        file.parentFile?.mkdirs()
        file.writeText(json)
        return file.absolutePath
    }
}
