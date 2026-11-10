package com.gamut.settings

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.StatFs
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelemetryCollector @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val app = context.applicationContext
    private val lock = Any()
    private val crumbs = ArrayDeque<String>()
    private val installed = AtomicBoolean(false)
    private val startedAt = System.currentTimeMillis()
    private val crashFile = File(File(app.filesDir, "telemetry").apply { mkdirs() }, "last_crash.txt")

    fun breadcrumb(tag: String, message: String) {
        val line = "+${(System.currentTimeMillis() - startedAt) / 1000}s ${tag.take(20)}: ${scrub(message).take(160)}"
        synchronized(lock) {
            crumbs.addLast(line)
            while (crumbs.size > MAX_CRUMBS) crumbs.removeFirst()
        }
    }

    fun recordError(tag: String, t: Throwable) {
        val where = t.stackTrace.take(3).joinToString(" < ") { "${it.fileName}:${it.lineNumber}" }
        breadcrumb("error/$tag", "${t.javaClass.simpleName}: ${t.message.orEmpty()} @ $where")
    }

    fun install() {
        if (!installed.compareAndSet(false, true)) return

        (app as? Application)?.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(a: Activity, s: Bundle?) = breadcrumb("lifecycle", "${a.javaClass.simpleName} created")
            override fun onActivityStarted(a: Activity) = breadcrumb("lifecycle", "${a.javaClass.simpleName} started")
            override fun onActivityStopped(a: Activity) = breadcrumb("lifecycle", "${a.javaClass.simpleName} stopped")
            override fun onActivityDestroyed(a: Activity) = breadcrumb("lifecycle", "${a.javaClass.simpleName} destroyed")
            override fun onActivityResumed(a: Activity) = Unit
            override fun onActivityPaused(a: Activity) = Unit
            override fun onActivitySaveInstanceState(a: Activity, o: Bundle) = Unit
        })

        app.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) breadcrumb("memory", "trim level $level")
            }
            override fun onLowMemory() = breadcrumb("memory", "low memory")
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
        })

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                writeCrash(thread, error)
            } catch (_: Exception) { }
            previous?.uncaughtException(thread, error)
        }
    }

    fun snapshot(extras: Map<String, String> = emptyMap()): String {
        val out = StringBuilder()
        fun line(k: String, v: Any?) {
            out.append(k).append(": ").append(v).append('\n')
        }
        fun safe(block: () -> Unit) {
            try {
                block()
            } catch (_: Exception) { }
        }

        safe {
            val info = app.packageManager.getPackageInfo(app.packageName, 0)
            line("app", "${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)})")
            line("debuggable", (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0)
        }
        line("device", "${Build.MANUFACTURER} ${Build.MODEL}")
        line("android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), patch ${Build.VERSION.SECURITY_PATCH}")
        line("abi", Build.SUPPORTED_ABIS.firstOrNull())
        line("locale", Locale.getDefault().toLanguageTag())
        line("uptime", "${(System.currentTimeMillis() - startedAt) / 1000}s")
        safe {
            val dm = app.resources.displayMetrics
            line("screen", "${dm.widthPixels}x${dm.heightPixels} @${dm.densityDpi}dpi")
            val night = (app.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            line("system_dark", night)
        }
        safe {
            val am = app.getSystemService(ActivityManager::class.java)
            val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            line("ram", "${mi.availMem shr 20} of ${mi.totalMem shr 20} MB free, low=${mi.lowMemory}")
            line("heap_limit", "${am.memoryClass} MB")
            val rt = Runtime.getRuntime()
            line("app_heap_used", "${(rt.totalMemory() - rt.freeMemory()) shr 20} MB")
        }
        safe { line("storage_free", "${StatFs(app.filesDir.path).availableBytes shr 20} MB") }
        safe {
            val b = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) line("battery", "${level * 100 / scale}%")
            line("power_save", app.getSystemService(PowerManager::class.java).isPowerSaveMode)
        }
        safe {
            val cm = app.getSystemService(ConnectivityManager::class.java)
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            val kind = when {
                caps == null -> "none"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                else -> "other"
            }
            val metered = !(caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ?: false)
            line("network", "$kind, metered=$metered, vpn=${caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ?: false}")
        }
        safe {
            val am = app.getSystemService(AudioManager::class.java)
            line("audio_outputs", am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { outputName(it.type) }.distinct().joinToString())
        }
        safe {
            if (crashFile.exists() && System.currentTimeMillis() - crashFile.lastModified() < CRASH_TTL_MS) {
                out.append("\n-- last crash --\n").append(crashFile.readText().take(3_500)).append('\n')
            }
        }
        if (extras.isNotEmpty()) {
            out.append("\n-- audio settings --\n")
            extras.forEach { (k, v) -> line(k, scrub(v)) }
        }
        val trail = synchronized(lock) { crumbs.toList() }
        if (trail.isNotEmpty()) {
            out.append("\n-- recent events --\n")
            trail.forEach { out.append(it).append('\n') }
        }
        return out.toString().take(MAX_CHARS)
    }

    private fun writeCrash(thread: Thread, error: Throwable) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val text = buildString {
            appendLine("time=${System.currentTimeMillis()} thread=${thread.name} uptime=${(System.currentTimeMillis() - startedAt) / 1000}s")
            append(trace.lineSequence().take(40).joinToString("\n"))
        }
        crashFile.writeText(scrub(text).take(6_000))
    }

    private fun outputName(type: Int) = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "speaker"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "earpiece"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "bluetooth-a2dp"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "bluetooth-sco"
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "usb"
        AudioDeviceInfo.TYPE_HDMI -> "hdmi"
        else -> "other($type)"
    }

    companion object {
        private const val MAX_CRUMBS = 40
        private const val MAX_CHARS = 7_000
        private const val CRASH_TTL_MS = 7L * 24 * 60 * 60 * 1000

        private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
        private val URL_QUERY = Regex("(https?://[^\\s?#]+)[?#]\\S*")
        private val TOKEN = Regex("\\b[A-Za-z0-9_-]{32,}\\b")
        private val IPV4 = Regex("\\b\\d{1,3}(\\.\\d{1,3}){3}\\b")

        fun scrub(s: String): String = s
            .replace(EMAIL, "<email>")
            .replace(URL_QUERY, "\$1?<redacted>")
            .replace(TOKEN, "<redacted>")
            .replace(IPV4, "<ip>")
    }
}
