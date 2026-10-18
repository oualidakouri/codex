package fr.maisonos.diagnostic

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaCodecList
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.*
import java.net.Inet4Address

data class WifiLan(val network: Network, val address: String, val broadcast: String, val prefix: Int)
data class Resources(val pssMb: Long, val heapMb: Long, val totalRamMb: Long, val availableRamMb: Long,
    val battery: Int, val charging: Boolean, val temperatureC: Float?, val thermal: Int, val elapsed: Long) {
    val protect: Boolean get() = (Build.VERSION.SDK_INT >= 29 && thermal >= PowerManager.THERMAL_STATUS_SEVERE) || (battery in 0..10 && !charging)
}

object DeviceDiagnostics {
    fun wifi(context: Context): WifiLan? {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        @Suppress("DEPRECATION")
        val networks = cm.allNetworks
        for (n in networks) {
            if (cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) continue
            val address = cm.getLinkProperties(n)?.linkAddresses?.firstOrNull { it.address is Inet4Address } ?: continue
            val octets = address.address.address
            val ip = octets.fold(0) { a, b -> (a shl 8) or (b.toInt() and 255) }
            val mask = if (address.prefixLength == 0) 0 else -1 shl (32 - address.prefixLength)
            val bc = ip or mask.inv()
            val broadcast = (3 downTo 0).joinToString(".") { ((bc ushr (it * 8)) and 255).toString() }
            return WifiLan(n, address.address.hostAddress.orEmpty(), broadcast, address.prefixLength)
        }
        return null
    }
    fun sample(context: Context, started: Long): Resources {
        val info = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
        val b = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val temp = b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val r = Runtime.getRuntime()
        return Resources(Debug.getPss().toLong() / 1024, (r.totalMemory() - r.freeMemory()) / 1048576,
            info.totalMem / 1048576, info.availMem / 1048576, if (scale > 0 && level >= 0) level * 100 / scale else -1,
            (b?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0,
            temp?.takeIf { it != Int.MIN_VALUE }?.div(10f),
            if (Build.VERSION.SDK_INT >= 29) context.getSystemService(PowerManager::class.java).currentThermalStatus else 0,
            SystemClock.elapsedRealtime() - started)
    }
    fun report(context: Context): String = buildString {
        val dm = context.resources.displayMetrics
        appendLine("${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT} · correctif ${Build.VERSION.SECURITY_PATCH}")
        appendLine("Affichage courant : ${dm.widthPixels} × ${dm.heightPixels} px · densité ${dm.densityDpi} dpi")
        appendLine("ABI : ${Build.SUPPORTED_ABIS.joinToString()} · processeurs visibles : ${Runtime.getRuntime().availableProcessors()}")
        appendLine("Décodeurs vidéo (déclaration Android ; décodage réel à tester) :")
        for (codec in MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }) {
            val types = codec.supportedTypes.filter { it.startsWith("video/") }
            if (types.isEmpty()) continue
            val hw = if (Build.VERSION.SDK_INT >= 29) if (codec.isHardwareAccelerated) "matériel déclaré" else "logiciel déclaré" else "type inconnu (API < 29)"
            appendLine("• ${codec.name} · $hw · ${types.joinToString()}")
        }
        append("La RAM totale est celle exposée par Android ; l’extension de stockage n’est pas de la RAM physique. La température affichée est celle de la batterie, pas du processeur.")
    }
}
