package com.ntstudio.clock

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * THEME 2 — Đồng hồ số kiểu Odometer (HH:MM:SS) với hiệu ứng cuộn spring physics.
 *
 * Bố cục: Dương lịch (20%) / Đồng hồ số (55%) / Âm lịch + Can Chi (25%).
 */
class DigitalClockActivity : AppCompatActivity() {

    private lateinit var odometerClock: OdometerClockView
    private lateinit var tvSolarDate: TextView
    private lateinit var tvLunarDate: TextView
    private lateinit var tvLunarCanChi: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val calendar = Calendar.getInstance()

    /**
     * Dương lịch: {thứ} - {ngày} - {tháng} - {năm}, ví dụ "Thứ Ba - 10 - 11 - 2026".
     * Locale tiếng Việt để "Thứ Ba" đúng chuẩn thay vì "Tuesday".
     */
    private val solarFormatter =
        SimpleDateFormat("EEEE - dd - MM - yyyy", Locale.forLanguageTag("vi-VN"))

    private var lastRenderedDayOfYear = -1
    private var lastRenderedYear = -1

    /**
     * Tick mỗi giây: cập nhật ngày tháng + đẩy giá trị mới vào đồng hồ.
     * Đồng hồ tự lo phần animation mượt giữa các frame.
     */
    private val ticker = object : Runnable {
        override fun run() {
            odometerClock.refresh()
            updateDateViews()
            handler.postDelayed(this, 1000L - (System.currentTimeMillis() % 1000L))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_digital_clock)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        odometerClock = findViewById(R.id.odometerClock)
        tvSolarDate = findViewById(R.id.tvDigitalSolarDate)
        tvLunarDate = findViewById(R.id.tvDigitalLunarDate)
        tvLunarCanChi = findViewById(R.id.tvDigitalLunarCanChi)

        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        updateDateViews()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    private fun updateDateViews() {
        calendar.timeInMillis = System.currentTimeMillis()

        val year = calendar.get(Calendar.YEAR)
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        if (year == lastRenderedYear && dayOfYear == lastRenderedDayOfYear) return
        lastRenderedYear = year
        lastRenderedDayOfYear = dayOfYear

        val now: Date = calendar.time

        // Hàng 1: Dương lịch
        tvSolarDate.text = solarFormatter.format(now)

        // Hàng 3: Âm lịch
        val lunar = LunarCalendar.fromSolar(
            day = calendar.get(Calendar.DAY_OF_MONTH),
            month = calendar.get(Calendar.MONTH) + 1,
            year = year
        )

        tvLunarDate.text = String.format(
            Locale.getDefault(),
            "%02d - %02d - %04d",
            lunar.day,
            lunar.month,
            lunar.year
        )

        val leapSuffix = if (lunar.isLeapMonth) " (nhuận)" else ""
        tvLunarCanChi.text = String.format(
            Locale.getDefault(),
            "Năm %s · Tháng %s%s · Ngày %s",
            lunar.yearCanChi,
            lunar.monthCanChi,
            leapSuffix,
            lunar.dayCanChi
        )
    }
}
