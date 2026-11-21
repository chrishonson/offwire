package com.chrishonson.gemmavoice.bench

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIDevice

/** Writes reports to Documents/bench. The bridge tries the GPU first and falls back to the CPU. */
class IosBenchmarkHost : BenchmarkHost {
    override val deviceModel: String = UIDevice.currentDevice.model
    override val backend: String = "gpu"

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override fun writeReport(name: String, json: String): String {
        val documents =
            NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String
        val dir = "$documents/bench"
        NSFileManager.defaultManager.createDirectoryAtPath(
            dir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
        val path = "$dir/$name"
        NSString.create(string = json)
            .writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        return path
    }
}
