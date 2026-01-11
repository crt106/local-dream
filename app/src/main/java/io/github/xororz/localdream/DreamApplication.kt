package io.github.xororz.localdream

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import xcrash.XCrash

class DreamApplication : Application() {
    companion object {
        private const val TAG = "DreamApplication"
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        
        // Initialize xCrash to catch Java, Native and ANR crashes
        XCrash.init(this, XCrash.InitParameters()
            .setAppVersion(BuildConfig.VERSION_NAME)
            .setJavaDumpAllThreads(true)
            .setNativeDumpAllThreads(true)
            .setJavaCallback { logPath: String?, _: String? ->
                Log.e(TAG, "Java crash occurred, log: $logPath")
                startErrorActivity(logPath)
            }
            .setNativeCallback { logPath: String?, _: String? ->
                Log.e(TAG, "Native crash occurred, log: $logPath")
                startErrorActivity(logPath)
            }
            .setAnrCallback { logPath: String?, _: String? ->
                Log.e(TAG, "ANR occurred, log: $logPath")
                startErrorActivity(logPath)
            }
        )
    }

    private fun startErrorActivity(logPath: String?) {
        val intent = Intent(this, CrashActivity::class.java).apply {
            putExtra("log_path", logPath)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }
}
