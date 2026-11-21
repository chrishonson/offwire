@file:JvmName("AndroidResourceFractionsActual")

package com.chrishonson.gemmavoice.resource

import kotlin.jvm.JvmName

actual fun cpuCoreCount(): Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
