package fr.maisonos.diagnostic

import android.app.*
import android.content.*
import android.media.*
import android.os.*
import androidx.core.app.NotificationCompat
import fr.maisonos.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

object AdhanPlayback {
    val title = MutableStateFlow<String?>(null)
    val error = MutableStateFlow<String?>(null)
    fun stop(context: Context) { AdhanScheduler.cancelTest(context); context.stopService(Intent(context, AdhanService::class.java)) }
    fun test(context: Context, mediaId: String, title: String = "Écoute de l’adhan") {
        context.startForegroundService(Intent(context, AdhanService::class.java).putExtra("media", mediaId).putExtra("title", title))
    }
}

object AdhanScheduler {
    private fun intent(context: Context, name: PrayerName): PendingIntent = PendingIntent.getBroadcast(context, 100 + name.ordinal,
        Intent(context, AdhanReceiver::class.java).setAction("fr.maisonos.ADHAN.${name.name}"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun exactAllowed(context: Context) = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    fun cancel(context: Context) { val alarms = context.getSystemService(AlarmManager::class.java); PrayerName.entries.forEach { alarms.cancel(intent(context, it)) } }
    fun cancelTest(context: Context) {
        val pending = PendingIntent.getBroadcast(context, 199, Intent(context, AdhanReceiver::class.java).setAction("fr.maisonos.TEST_ADHAN"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        context.getSystemService(AlarmManager::class.java).cancel(pending)
        context.getSharedPreferences("adhan-runtime", Context.MODE_PRIVATE).edit().putLong("planned-test", 0).apply()
    }
    fun scheduleTest(context: Context, delayMillis: Long = 10000) {
        require(delayMillis in 1000..60000 && exactAllowed(context))
        val pending = PendingIntent.getBroadcast(context, 199, Intent(context, AdhanReceiver::class.java).setAction("fr.maisonos.TEST_ADHAN"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val at = System.currentTimeMillis() + delayMillis
        context.getSharedPreferences("adhan-runtime", Context.MODE_PRIVATE).edit().putLong("planned-test", at).commit()
        context.getSystemService(AlarmManager::class.java).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }
    @Synchronized fun schedule(context: Context, config: ProbeConfig = ConfigStore(context).load(), now: Instant = Instant.now()): List<PrayerMoment> {
        cancel(context)
        val p = config.home.prayer
        val planned = PrayerEngine.nextAlarms(p, now)
        val alarms = context.getSystemService(AlarmManager::class.java)
        val prefs = context.getSharedPreferences("adhan-runtime", Context.MODE_PRIVATE)
        planned.forEach { event ->
            val key = event.prayer.name
            prefs.edit().putLong("planned-$key", event.epochMillis).apply()
            if (exactAllowed(context)) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, event.epochMillis, intent(context, event.prayer))
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, event.epochMillis, intent(context, event.prayer))
        }
        return planned
    }
}

class AdhanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val config = ConfigStore(context).load()
                val p = config.home.prayer
                if (intent.action == "fr.maisonos.TEST_ADHAN") {
                    val prefs = context.getSharedPreferences("adhan-runtime", Context.MODE_PRIVATE)
                    val expected = prefs.getLong("planned-test", 0)
                    if (expected > 0 && System.currentTimeMillis() - expected in -1000..60000 && p.audioId.isNotBlank()) {
                        prefs.edit().putLong("planned-test", 0).putLong("last-test-fired", System.currentTimeMillis()).commit()
                        runCatching { AdhanPlayback.test(context, p.audioId, "Test de l’alarme · audio personnel") }
                    }
                    return@launch
                }
                val name = PrayerName.entries.firstOrNull { intent.action == "fr.maisonos.ADHAN.${it.name}" } ?: return@launch
                val prefs = context.getSharedPreferences("adhan-runtime", Context.MODE_PRIVATE)
                val scheduled = prefs.getLong("planned-${name.name}", 0)
                val played = prefs.getLong("played-${name.name}", 0)
                val now = Instant.now()
                if (scheduled > 0 && scheduled != played && now.toEpochMilli() - scheduled in -1000..300000 && name.name in p.enabled && !PrayerEngine.quiet(p, now)) {
                    val mediaId = if (name == PrayerName.FAJR && p.fajrAudioId.isNotEmpty()) p.fajrAudioId else p.audioId
                    if (config.home.media.any { it.id == mediaId && it.kind == MediaKind.AUDIO } && LocalFiles.mediaFile(context, mediaId).exists()) {
                        // Persist before starting playback to prevent duplicate broadcasts from replaying.
                        prefs.edit().putLong("played-${name.name}", scheduled).commit()
                        runCatching { AdhanPlayback.test(context, mediaId, "Adhan · ${name.label}") }.onFailure {
                            AdhanPlayback.error.value = "Lecture refusée par Android. Vérifier autorisations et restrictions batterie."
                            prefs.edit().putString("last-error", "Lecture refusée par Android. Vérifier autorisations et restrictions batterie.").apply()
                        }
                    } else {
                        prefs.edit().putString("last-error", "Fichier audio absent pour la prière activée. Importer et choisir une récitation.").apply()
                    }
                }
                if (scheduled > 0 && now.toEpochMilli() - scheduled > 300000 && name.name in p.enabled) prefs.edit().putString("last-error", "Adhan non joué : alarme reçue avec plus de cinq minutes de retard. Vérifier les restrictions Android.").apply()
                runCatching { AdhanScheduler.schedule(context, config, now.plusSeconds(2)) }
            } catch (_: Exception) {
                context.getSharedPreferences("adhan-runtime", Context.MODE_PRIVATE).edit().putString("last-error", "Programmation impossible : vérifier la configuration de salat.").apply()
            } finally { result.finish() }
        }
    }
}

/** Boot never starts an activity/camera/media service; it only restores future alarms. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch { try { runCatching { AdhanScheduler.schedule(context) } } finally { result.finish() } }
    }
}

class AdhanService : Service() {
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private val handler = Handler(Looper.getMainLooper())
    private val stopTimeout = Runnable { stopSelf() }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        val channel = NotificationChannel("adhan", "Adhan et lecture audio", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 0, Intent(this, AdhanService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val title = intent?.getStringExtra("title") ?: "Adhan"
        val notification = NotificationCompat.Builder(this, "adhan").setSmallIcon(fr.maisonos.diagnostic.R.drawable.ic_maison)
            .setContentTitle(title).setContentText("Lecture locale · toucher pour ouvrir").setContentIntent(open).setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM).addAction(0, "Arrêter", stop).build()
        try {
            startForeground(2001, notification)
            val mediaId = intent?.getStringExtra("media") ?: error("Audio absent")
            val config = ConfigStore(this).load()
            check(config.home.media.any { it.id == mediaId && it.kind == MediaKind.AUDIO })
            val file = LocalFiles.mediaFile(this, mediaId); check(file.exists())
            player?.release(); player = null
            val manager = getSystemService(AudioManager::class.java)
            focus?.let(manager::abandonAudioFocusRequest)
            val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener { change -> if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stopSelf() }.build()
            focus = request
            check(manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            AdhanPlayback.error.value = null
            player = MediaPlayer().apply {
                setAudioAttributes(attributes); setWakeMode(this@AdhanService, PowerManager.PARTIAL_WAKE_LOCK)
                setDataSource(file.absolutePath); setVolume(config.home.prayer.volume, config.home.prayer.volume)
                setOnPreparedListener {
                    AdhanPlayback.title.value = title
                    getSharedPreferences("adhan-runtime", MODE_PRIVATE).edit().remove("last-error").putLong("last-prepared", System.currentTimeMillis()).apply()
                    it.start()
                }
                setOnCompletionListener { stopSelf() }
                setOnErrorListener { _, _, _ -> AdhanPlayback.error.value = "Fichier audio illisible ou format non pris en charge."; stopSelf(); true }
                prepareAsync()
            }
            handler.removeCallbacks(stopTimeout); handler.postDelayed(stopTimeout, 10 * 60 * 1000L)
        } catch (_: Exception) { AdhanPlayback.error.value = "Lecture impossible. Vérifier fichier, autorisations et disponibilité audio."; stopSelf() }
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        handler.removeCallbacks(stopTimeout)
        player?.release(); player = null
        focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        AdhanPlayback.title.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
