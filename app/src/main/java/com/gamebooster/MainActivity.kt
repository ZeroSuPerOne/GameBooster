package com.gamebooster

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var activityManager: ActivityManager
    private lateinit var tvStatus: TextView
    private lateinit var tvRamBefore: TextView
    private lateinit var tvRamAfter: TextView
    private lateinit var tvAppsKilled: TextView
    private lateinit var btnBoost: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutResult: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        tvStatus = findViewById(R.id.tvStatus)
        tvRamBefore = findViewById(R.id.tvRamBefore)
        tvRamAfter = findViewById(R.id.tvRamAfter)
        tvAppsKilled = findViewById(R.id.tvAppsKilled)
        btnBoost = findViewById(R.id.btnBoost)
        progressBar = findViewById(R.id.progressBar)
        layoutResult = findViewById(R.id.layoutResult)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GameBooster::WakeLock")
        btnBoost.setOnClickListener { startBoost() }
        updateRamDisplay()
    }

    private fun getRamInfo(): Triple<Long, Long, Long> {
        val mi = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(mi)
        val total = mi.totalMem / (1024*1024)
        val avail = mi.availMem / (1024*1024)
        return Triple(total, avail, total - avail)
    }

    private fun updateRamDisplay() {
        val (total, avail, used) = getRamInfo()
        tvRamBefore.text = "RAM ใช้อยู่: ${used}MB / ${total}MB\nว่าง: ${avail}MB"
    }

    private fun killBackgroundApps(): Int {
        val running = activityManager.runningAppProcesses ?: return 0
        var killed = 0
        for (p in running) {
            if (p.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE) {
                try {
                    val info = packageManager.getApplicationInfo(p.processName, 0)
                    if ((info.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && p.processName != packageName) {
                        activityManager.killBackgroundProcesses(p.processName)
                        killed++
                    }
                } catch (e: Exception) {}
            }
        }
        return killed
    }

    private fun startBoost() {
        btnBoost.isEnabled = false
        progressBar.visibility = android.view.View.VISIBLE
        layoutResult.visibility = android.view.View.GONE
        tvStatus.text = "⚡ กำลังบูสต์..."
        if (!wakeLock.isHeld) wakeLock.acquire(10 * 60 * 1000L)
        val ramBefore = getRamInfo()
        CoroutineScope(Dispatchers.IO).launch {
            val killed = killBackgroundApps()
            delay(500)
            System.gc(); Runtime.getRuntime().gc()
            delay(800)
            val ramAfter = getRamInfo()
            val freed = ramAfter.second - ramBefore.second
            withContext(Dispatchers.Main) {
                progressBar.visibility = android.view.View.GONE
                layoutResult.visibility = android.view.View.VISIBLE
                tvStatus.text = "✅ บูสต์เสร็จแล้ว!"
                btnBoost.isEnabled = true
                tvRamBefore.text = "RAM ก่อนบูสต์: ${ramBefore.third}MB"
                tvRamAfter.text = "RAM หลังบูสต์: ${ramAfter.third}MB\n✅ ว่างเพิ่ม: +${freed}MB"
                tvAppsKilled.text = "🔴 แอปที่ปิด: $killed แอป"
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (wakeLock.isHeld) wakeLock.release()
    }
}
