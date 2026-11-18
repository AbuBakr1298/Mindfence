package com.example.mindfence

import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("mindfence", MODE_PRIVATE) }
    private var focusTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ensureNewDay()
        bindNavigation()
        bindHome()
        bindLimits()
        bindFocus()
        refreshStats()
    }

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)

    private fun ensureNewDay() {
        val storedDay = prefs.getString("day", "")
        val today = todayKey()
        if (storedDay != today) {
            prefs.edit()
                .putString("day", today)
                .putInt("todayCount", 0)
                .apply()
        }
    }

    private fun bindNavigation() {
        val views = mapOf(
            R.id.homeView to findViewById<ScrollView>(R.id.homeView),
            R.id.limitsView to findViewById<ScrollView>(R.id.limitsView),
            R.id.focusView to findViewById<ScrollView>(R.id.focusView),
            R.id.statsView to findViewById<ScrollView>(R.id.statsView)
        )
        findViewById<BottomNavigationView>(R.id.bottomNav).setOnItemSelectedListener { item ->
            views.values.forEach { it.visibility = View.GONE }
            val selected = when (item.itemId) {
                R.id.nav_limits -> R.id.limitsView
                R.id.nav_focus -> R.id.focusView
                R.id.nav_stats -> R.id.statsView
                else -> R.id.homeView
            }
            views[selected]?.visibility = View.VISIBLE
            if (selected == R.id.statsView) refreshStats()
            true
        }
    }

    private fun bindHome() {
        refreshHome()
        findViewById<MaterialButton>(R.id.logReelButton).setOnClickListener {
            ensureNewDay()
            val count = prefs.getInt("todayCount", 0) + 1
            prefs.edit().putInt("todayCount", count).apply()
            val today = todayKey()
            prefs.edit().putInt("history_$today", count).apply()
            refreshHome()
        }
    }

    private fun refreshHome() {
        ensureNewDay()
        val count = prefs.getInt("todayCount", 0)
        val limit = prefs.getInt("dailyLimit", 20)
        findViewById<TextView>(R.id.countText).text = "$count / $limit"
        findViewById<ProgressBar>(R.id.dailyProgress).apply {
            max = limit
            progress = count.coerceAtMost(limit)
        }
        findViewById<TextView>(R.id.statusText).text =
            if (count >= limit) "Daily limit reached. A mindful pause is ready in Limits."
            else "You are within your daily limit."
    }

    private fun bindLimits() {
        val limitSlider = findViewById<Slider>(R.id.limitSlider)
        val limitValue = findViewById<TextView>(R.id.limitValue)
        val limit = prefs.getInt("dailyLimit", 20).toFloat()
        limitSlider.value = limit
        limitValue.text = "${limit.toInt()} reels"
        limitSlider.addOnChangeListener { _, value, _ ->
            limitValue.text = "${value.toInt()} reels"
        }
        findViewById<MaterialButton>(R.id.saveLimitButton).setOnClickListener {
            prefs.edit().putInt("dailyLimit", limitSlider.value.toInt()).apply()
            refreshHome()
            limitValue.text = "${limitSlider.value.toInt()} reels saved"
        }
        findViewById<MaterialButton>(R.id.challengeButton).setOnClickListener {
            startPauseChallenge()
        }
    }

    private fun startPauseChallenge() {
        AlertDialog.Builder(this)
            .setTitle("30-second mindful pause")
            .setMessage("Put your phone down. Stay still until the timer finishes.")
            .setPositiveButton("Start") { dialog, _ ->
                dialog.dismiss()
                val seconds = 30
                AlertDialog.Builder(this)
                    .setTitle("Pause: $seconds")
                    .setMessage("Keep your phone face down.")
                    .setCancelable(false)
                    .setPositiveButton("Complete") { _, _ -> }
                    .create()
                    .also { alert ->
                        alert.show()
                        object : CountDownTimer(seconds * 1000L, 1000) {
                            override fun onTick(left: Long) {
                                val remaining = (left / 1000).toInt()
                                alert.setTitle("Pause: $remaining")
                                alert.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = remaining == 0
                            }

                            override fun onFinish() {
                                alert.setTitle("Pause complete")
                                alert.setMessage("You chose to take a break.")
                                alert.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                            }
                        }.start()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun bindFocus() {
        val timeView = findViewById<TextView>(R.id.focusTime)
        val status = findViewById<TextView>(R.id.focusStatus)
        val button = findViewById<MaterialButton>(R.id.focusButton)
        var running = false
        button.setOnClickListener {
            if (running) {
                focusTimer?.cancel()
                running = false
                button.text = "Start focus"
                status.text = "Session paused."
                return@setOnClickListener
            }
            running = true
            button.text = "Pause"
            status.text = "Stay with one task."
            val secondsLeft = when (timeView.text) {
                "25:00" -> 25 * 60
                else -> 25 * 60
            }
            focusTimer?.cancel()
            focusTimer = object : CountDownTimer(secondsLeft * 1000L, 1000) {
                override fun onTick(left: Long) {
                    val minutes = left / 1000 / 60
                    val seconds = left / 1000 % 60
                    timeView.text = String.format(Locale.US, "%02d:%02d", minutes, seconds)
                }

                override fun onFinish() {
                    running = false
                    timeView.text = "25:00"
                    button.text = "Start again"
                    status.text = "Focus session complete."
                }
            }.start()
        }
    }

    private fun refreshStats() {
        val bars = findViewById<LinearLayout>(R.id.statsBars)
        bars.removeAllViews()
        val calendar = Calendar.getInstance()
        val counts = mutableListOf<Int>()
        val labels = mutableListOf<String>()
        repeat(7) { offset ->
            calendar.timeInMillis = Calendar.getInstance().timeInMillis
            calendar.add(Calendar.DAY_OF_YEAR, -6 + offset)
            val key = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
            counts.add(prefs.getInt("history_$key", 0))
            labels.add(SimpleDateFormat("EEE", Locale.US).format(calendar.time).take(2))
        }
        val max = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
        counts.forEachIndexed { index, count ->
            val column = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            }
            val bar = View(this).apply {
                val height = (count * 150 / max).coerceAtLeast(if (count > 0) 8 else 2)
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(height))
                setBackgroundColor(if (index == 6) getColor(R.color.primary) else getColor(R.color.accent))
            }
            val label = TextView(this).apply {
                text = labels[index]
                textSize = 11f
                setPadding(0, dp(8), 0, 0)
                setTextColor(getColor(R.color.muted))
            }
            column.addView(bar)
            column.addView(label)
            bars.addView(column)
        }
        val total = counts.sum()
        findViewById<TextView>(R.id.statsSummary).text =
            if (total == 0) "No activity logged in the last 7 days."
            else "$total reels logged across the last 7 days."
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
