@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package fr.maisonos.diagnostic

import android.app.*
import android.content.*
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.media3.common.*
import androidx.media3.datasource.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import fr.maisonos.core.*
import kotlinx.coroutines.flow.MutableStateFlow

 data class QuranAudioState(val active: Boolean = false, val playing: Boolean = false, val buffering: Boolean = false, val title: String = "", val error: String = "", val positionMs: Long = 0, val durationMs: Long = 0, val seekable: Boolean = false, val volume: Float = 0.8f)
object QuranPlayback {
    val state = MutableStateFlow(QuranAudioState())
    fun play(context: Context, settings: QuranSettings) {
        if (AdhanPlayback.active.value) { state.value = state.value.copy(error = "L’adhan est en cours. Attendre sa fin pour lancer la récitation."); return }
        try {
            settings.playbackUrl()
            context.startForegroundService(Intent(context,QuranService::class.java))
        } catch (_: Exception) { state.value = QuranAudioState(error = "Lecture impossible. Vérifier l’URL et les réglages du lecteur.") }
    }
    fun stop(context: Context) {
        context.stopService(Intent(context,QuranService::class.java))
        state.value = QuranAudioState()
    }
    fun seek(context: Context, positionMs: Long) {
        if (state.value.active && state.value.seekable) runCatching {
            context.startService(Intent(context,QuranService::class.java).setAction("SEEK").putExtra("position",positionMs))
        }
    }
    fun volume(context: Context, value: Float) {
        if (state.value.active) runCatching { context.startService(Intent(context,QuranService::class.java).setAction("VOLUME").putExtra("volume",value.coerceIn(0f,1f))) }
    }
}

/** Playback starts only after a user's Play action; never at boot or after process death. */
class QuranService : Service() {
    private var player: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            player?.let { p -> QuranPlayback.state.value = QuranPlayback.state.value.copy(
                positionMs = p.currentPosition.coerceAtLeast(0), durationMs = p.duration.takeIf { it > 0 } ?: 0,
                seekable = p.isCurrentMediaItemSeekable && p.duration > 0, volume = p.volume) }
            handler.postDelayed(this,500)
        }
    }
    override fun onCreate() { super.onCreate(); handler.post(ticker) }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        if (intent?.action == "SEEK") {
            player?.let { if (it.isCurrentMediaItemSeekable && it.duration > 0) it.seekTo(intent.getLongExtra("position",0).coerceIn(0,it.duration)) }
            if (player == null) stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == "VOLUME") { player?.volume = intent.getFloatExtra("volume",0.8f).coerceIn(0f,1f); if (player == null) stopSelf(); return START_NOT_STICKY }
        try {
            getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("quran","Lecteur de Coran",NotificationManager.IMPORTANCE_LOW))
            val open = PendingIntent.getActivity(this,20,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val stop = PendingIntent.getService(this,21,Intent(this,QuranService::class.java).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = NotificationCompat.Builder(this,"quran").setSmallIcon(R.drawable.ic_maison).setContentTitle("Lecteur de Coran")
                .setContentText("Chargement audio…").setContentIntent(open).setOngoing(true).setCategory(NotificationCompat.CATEGORY_TRANSPORT).addAction(0,"Arrêter",stop).build()
            startForeground(2002,notification)
            check(!AdhanPlayback.active.value)
            val settings = ConfigStore(this).load().home.quran
            val url = settings.playbackUrl()
            player?.release()
            QuranPlayback.state.value = QuranAudioState(active = true,buffering = true,title = settings.title,volume = settings.volume)
            val http = DefaultHttpDataSource.Factory().setUserAgent("MaisonOS-Quran").setConnectTimeoutMs(10000).setReadTimeoutMs(15000).setAllowCrossProtocolRedirects(false)
            val factory = DefaultMediaSourceFactory(DefaultDataSource.Factory(this,http))
            player = ExoPlayer.Builder(this).setMediaSourceFactory(factory).build().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),true)
                setHandleAudioBecomingNoisy(true); setWakeMode(C.WAKE_MODE_LOCAL)
                trackSelectionParameters = trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO,true).build()
                volume = settings.volume
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) { QuranPlayback.state.value = QuranPlayback.state.value.copy(playing = isPlaying) }
                    override fun onPlaybackStateChanged(state: Int) {
                        QuranPlayback.state.value = QuranPlayback.state.value.copy(buffering = state == Player.STATE_BUFFERING)
                        if (state == Player.STATE_ENDED) stopSelf()
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        QuranPlayback.state.value = QuranAudioState(error = "Flux audio indisponible (${error.errorCodeName}). Vérifier réseau, URL ou catalogue puis relancer.")
                        stopSelf()
                    }
                })
                setMediaItem(MediaItem.fromUri(url)); prepare(); playWhenReady = true
            }
            getSystemService(NotificationManager::class.java).notify(2002,NotificationCompat.Builder(this,"quran").setSmallIcon(R.drawable.ic_maison)
                .setContentTitle("Lecteur de Coran").setContentText(settings.title).setContentIntent(open).setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT).addAction(0,"Arrêter",stop).build())
        } catch (_: Exception) { QuranPlayback.state.value = QuranAudioState(error = "Lecture refusée. Vérifier le flux réseau, le catalogue et les autorisations Android."); stopSelf() }
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        player?.release(); player = null
        QuranPlayback.state.value = QuranAudioState(error = QuranPlayback.state.value.error)
        stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy()
    }
}
