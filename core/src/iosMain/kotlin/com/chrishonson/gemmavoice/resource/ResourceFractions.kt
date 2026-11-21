package com.chrishonson.gemmavoice.resource

import platform.Foundation.NSProcessInfo

actual fun cpuCoreCount(): Int = NSProcessInfo.processInfo.activeProcessorCount.toInt().coerceAtLeast(1)
