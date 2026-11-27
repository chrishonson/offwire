@file:OptIn(ExperimentalForeignApi::class)

package com.chrishonson.gemmavoice.model

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Network.nw_path_get_status
import platform.Network.nw_path_is_constrained
import platform.Network.nw_path_is_expensive
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_get_main_queue

/**
 * Wi-Fi and wired paths count as unmetered. Cellular, a personal hotspot and Low Data Mode count as metered, so
 * the model download waits for Wi-Fi unless the user allows cellular, as the device check promises. Updates arrive
 * on the main queue, where `ChatStore` reads them.
 */
class IosNetworkMonitor : NetworkMonitor {
    private var unmetered = false
    private val monitor = nw_path_monitor_create()

    init {
        nw_path_monitor_set_update_handler(monitor) { path ->
            unmetered = nw_path_get_status(path) == nw_path_status_satisfied &&
                !nw_path_is_expensive(path) &&
                !nw_path_is_constrained(path)
        }
        nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
        nw_path_monitor_start(monitor)
    }

    override fun isUnmetered(): Boolean = unmetered
}
