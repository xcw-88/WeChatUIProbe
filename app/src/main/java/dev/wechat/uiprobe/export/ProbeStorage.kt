package dev.wechat.uiprobe.export

import android.content.Context
import java.io.File

object ProbeStorage {
    // One exporter lock for UI clear/load and service scans in the same process.
    @Volatile private var instance: ProbeExporter? = null
    fun exporter(context: Context): ProbeExporter = instance ?: synchronized(this) {
        instance ?: ProbeExporter(File(context.applicationContext.filesDir, "probe_exports")).also { instance = it }
    }
}
