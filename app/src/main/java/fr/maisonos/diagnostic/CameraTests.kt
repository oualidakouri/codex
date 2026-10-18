@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package fr.maisonos.diagnostic

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView
import fr.maisonos.core.*
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

@Composable
fun RtspVideo(config: CameraConfig, vm: ProbeViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var attempt by remember(config.id, config.rtspUrl, config.rtspVendorPath) { mutableIntStateOf(0) }
    var retry by remember(config.id, config.rtspUrl, config.rtspVendorPath) { mutableStateOf(false) }
    var errorCode by remember(config.id, config.rtspUrl, config.rtspVendorPath) { mutableStateOf<String?>(null) }
    var connectedAt by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    if (retry && attempt < 5) {
        LaunchedEffect(attempt, retry) {
            delay((2000L shl attempt).coerceAtMost(30000L))
            attempt++; retry = false; errorCode = null
        }
    }
    val player = remember(config, attempt) {
        connectedAt = SystemClock.elapsedRealtime()
        val sockets = DeviceDiagnostics.wifi(context)?.network?.socketFactory
        if (sockets == null) null else ExoPlayer.Builder(context).build().apply {
            volume = 0f // Live-view audio is muted; audio testing is explicit.
            val source = RtspMediaSource.Factory().setForceUseRtpTcp(true).setTimeoutMs(5000)
                .setSocketFactory(sockets).createMediaSource(MediaItem.fromUri(LocalEndpoint.authenticatedRtsp(config)))
            setMediaSource(source)
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val (hint, reconnect) = CameraTroubleshooting.rtsp(error)
                errorCode = hint; retry = reconnect
                vm.record("RTSP", Evidence.FAILED, "${config.name} · $hint ${if (reconnect && attempt < 5) "Reconnexion programmée ($attempt/5)." else "Relancer manuellement après correction."}")
            }
        }
        val analytics = object : AnalyticsListener {
            override fun onRenderedFirstFrame(eventTime: AnalyticsListener.EventTime, output: Any, renderTimeMs: Long) {
                vm.record("RTSP", Evidence.OBSERVED, "Première image rendue en ${SystemClock.elapsedRealtime() - connectedAt} ms depuis la création du lecteur. Latence caméra-écran et fluidité à vérifier visuellement.")
            }
            override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
                vm.log("Décodeur réellement choisi : $decoderName · initialisation ${initializationDurationMs} ms.")
            }
            override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
                vm.log("Vidéo : $droppedFrames image(s) perdues sur $elapsedMs ms.")
            }
        }
        player?.addListener(listener); player?.addAnalyticsListener(analytics); player?.prepare(); player?.playWhenReady = true
        if (player == null) vm.record("RTSP", Evidence.FAILED, "Wi-Fi absent. Arrêter puis relancer le test après reconnexion.")
        onDispose { player?.removeListener(listener); player?.removeAnalyticsListener(analytics); player?.release() }
    }
    Column(modifier) {
        AndroidView(factory = { PlayerView(it).apply { useController = false; this.player = player; keepScreenOn = false } },
            update = { it.player = player }, modifier = Modifier.fillMaxWidth().weight(1f))
        errorCode?.let { androidx.compose.material3.Text(it, fontSize = 11.sp, maxLines = 3) }
    }
}

@Composable
fun PresenceCamera(vm: ProbeViewModel, showPreview: Boolean, trackFace: Boolean = false) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    DisposableEffect(lifecycle, trackFace) {
        val disposed = AtomicBoolean(false)
        val executor = Executors.newSingleThreadExecutor()
        val detector = MotionDetector()
        val faces = FaceTracker()
        var faceFailures = 0
        val last = AtomicLong(0)
        var provider: ProcessCameraProvider? = null
        @Suppress("DEPRECATION")
        val analysis = ImageAnalysis.Builder().setTargetResolution(android.util.Size(320, 240)).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        @Suppress("DEPRECATION")
        val preview = Preview.Builder().setTargetResolution(android.util.Size(320, 240)).build().also { it.surfaceProvider = previewView.surfaceProvider }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!disposed.get()) {
                try {
                    check(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                    val p = future.get().also { provider = it }
                    check(p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA))
                    analysis.setAnalyzer(executor) { image ->
                        try {
                            val now = SystemClock.elapsedRealtime()
                            if (now - last.get() >= if (trackFace) 250 else 125) {
                                last.set(now)
                                if (trackFace) { val gaze = faces.detect(image); faceFailures = 0; vm.face(gaze); return@setAnalyzer }
                                val plane = image.planes[0]
                                val buffer = plane.buffer
                                val crop = image.cropRect
                                val samples = IntArray(16 * 12) { i ->
                                    val x = crop.left + (i % 16) * crop.width() / 16
                                    val y = crop.top + (i / 16) * crop.height() / 12
                                    buffer.get(buffer.position() + y * plane.rowStride + x * plane.pixelStride).toInt() and 255
                                }
                                vm.motion(detector.score(samples))
                            }
                        } catch (_: Exception) { if (trackFace && ++faceFailures >= 3) vm.pumpkinCameraFailed() }
                        finally { image.close() }
                    }
                    if (showPreview) p.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_FRONT_CAMERA, analysis, preview)
                    else p.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
                    if (trackFace) vm.pumpkinCameraReady() else vm.record("Présence", Evidence.RUNNING, "Analyse locale de mouvement (8 échantillons/s maximum), pas de reconnaissance de personne. Aucun enregistrement.")
                } catch (_: Exception) {
                    vm.record("Présence", Evidence.FAILED, "Caméra frontale indisponible, permission refusée ou caméra déjà utilisée.")
                    if (trackFace) vm.pumpkinCameraFailed() else vm.enablePresence(false)
                }
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed.set(true); analysis.clearAnalyzer(); provider?.unbind(analysis, preview); executor.shutdownNow(); detector.reset()
        }
    }
    AndroidView(factory = { previewView }, modifier = if (showPreview) Modifier.fillMaxWidth().height(160.dp) else Modifier.size(1.dp))
}
