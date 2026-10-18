package fr.maisonos.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.Closeable

/** Domain contracts stay Android/Compose independent for future local connectors/widgets. */
enum class DeviceCapability { VIDEO, PAN_TILT, TEMPERATURE, HUMIDITY, OPENING, SWITCH }
data class DeviceDescriptor(val id: String, val label: String, val capabilities: Set<DeviceCapability>)
data class WidgetDescriptor(val id: String, val deviceId: String?, val kind: String, val minWidthCells: Int = 1, val minHeightCells: Int = 1)
enum class PanTilt(val x: Double, val y: Double) { LEFT(-1.0, 0.0), RIGHT(1.0, 0.0), UP(0.0, 1.0), DOWN(0.0, -1.0) }
interface PtzPort : Closeable {
    fun move(direction: PanTilt, speed: Double)
    fun stop()
}
data class DeviceEvent(val deviceId: String, val type: String, val simulated: Boolean, val timestampMillis: Long = System.currentTimeMillis())
interface DeviceEventSource { val events: SharedFlow<DeviceEvent> }
class SimulationConnector : DeviceEventSource {
    private val bus = MutableSharedFlow<DeviceEvent>(extraBufferCapacity = 16)
    override val events = bus.asSharedFlow()
    suspend fun mailboxOpened() { bus.emit(DeviceEvent("simulation-mailbox", "opening", simulated = true)) }
}

/** Survives client replacement so a reconnect button cannot bypass the login budget. */
class LoginLimiter {
    private val attempts = mutableMapOf<String, ArrayDeque<Long>>()
    @Synchronized fun allow(endpoint: String, nowMillis: Long): Boolean {
        val times = attempts.getOrPut(endpoint) { ArrayDeque() }
        while (times.isNotEmpty() && nowMillis - times.first() >= 60000) times.removeFirst()
        if (times.size >= 3 || (times.isNotEmpty() && nowMillis - times.last() < 5000)) return false
        times.addLast(nowMillis)
        return true
    }
}
