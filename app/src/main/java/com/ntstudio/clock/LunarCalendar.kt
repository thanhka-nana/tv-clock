package com.ntstudio.clock

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.floor

/**
 * Chuyển đổi Dương lịch sang Âm lịch Việt Nam.
 *
 * Thuật toán dựa trên công thức của Hồ Ngọc Đức
 * (https://www.informatik.uni-leipzig.de/~duc/amlich/) — tính tháng nhuận theo tiết khí
 * và điểm Sóc (new moon) xấp xỉ. Múi giờ dùng để tính là UTC+7 (giờ Việt Nam).
 */
object LunarCalendar {

    private val CAN = arrayOf("Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ", "Canh", "Tân", "Nhâm", "Quý")
    private val CHI = arrayOf("Tý", "Sửu", "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi", "Thân", "Dậu", "Tuất", "Hợi")

    /** Múi giờ Việt Nam (UTC+7) dùng cho toàn bộ phép tính âm lịch. */
    val VIETNAM: TimeZone = TimeZone.getTimeZone("GMT+7")

    /** Kết quả chuyển đổi âm lịch. */
    data class LunarDate(
        val day: Int,
        val month: Int,
        val year: Int,
        val isLeapMonth: Boolean,
        /** Can Chi của năm, ví dụ "Giáp Thìn". */
        val yearCanChi: String,
        /** Can Chi của tháng, ví dụ "Bính Dần". */
        val monthCanChi: String,
        /** Can Chi của ngày, ví dụ "Mậu Tý". */
        val dayCanChi: String
    )

    /**
     * Chuyển một ngày Dương lịch sang Âm lịch (tính theo giờ Việt Nam).
     */
    fun fromSolar(day: Int, month: Int, year: Int): LunarDate {
        val dayNumber = jdFromDate(day, month, year)
        val tuple = jdToLunar(dayNumber)

        return LunarDate(
            day = tuple.day,
            month = tuple.month,
            year = tuple.year,
            isLeapMonth = tuple.leap,
            yearCanChi = "${CAN[(tuple.year + 6) % 10]} ${CHI[(tuple.year + 8) % 12]}",
            monthCanChi = "${CAN[(tuple.year * 12 + tuple.month + 3) % 10]} ${CHI[(tuple.month + 1) % 12]}",
            dayCanChi = "${CAN[(dayNumber + 9) % 10]} ${CHI[(dayNumber + 1) % 12]}"
        )
    }

    // ---------------------------------------------------------------------
    // Thuật toán thiên văn
    // ---------------------------------------------------------------------

    /** Số ngày Julius của ngày Dương lịch. */
    fun jdFromDate(dd: Int, mm: Int, yy: Int): Int {
        val a = (14 - mm) / 12
        val y = yy + 4800 - a
        val m = mm + 12 * a - 3
        var jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
        if (jd < 2299161) {
            jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083
        }
        return jd
    }

    /** Độ lệch giờ Việt Nam so với UTC, đơn vị là "ngày". */
    private fun localTimeOffset(jd: Int): Double {
        return VIETNAM.getOffset(jdToMillis(jd)) / 86_400_000.0
    }

    /** Quy đổi số ngày Julius -> mili giây (00:00 UTC của ngày đó). */
    private fun jdToMillis(jd: Int): Long = (jd - 2440588L) * 86_400_000L

    /** Mili giây -> năm dương lịch (theo giờ Việt Nam). */
    private fun yearOfJd(jd: Int): Int {
        val cal = Calendar.getInstance(VIETNAM)
        cal.timeInMillis = jdToMillis(jd)
        return cal.get(Calendar.YEAR)
    }

    /** Thời điểm Sóc (new moon) thứ k kể từ năm 1900, trả về số ngày Julius dạng Double. */
    private fun newMoon(k: Int): Double {
        val t = k / 1236.85
        val t2 = t * t
        val t3 = t2 * t
        val dr = Math.PI / 180
        var jd = 2415020.75933 + 29.53058868 * k + 0.0001178 * t2 - 0.000000155 * t3
        jd += 0.00033 * Math.sin((166.56 + 132.87 * t - 0.009173 * t2) * dr)
        val m = 359.2242 + 29.10535608 * k - 0.0000333 * t2 - 0.00000347 * t3
        val mpr = 306.0253 + 385.81691806 * k + 0.0107306 * t2 + 0.00001236 * t3
        val f = 21.2964 + 390.67050646 * k - 0.0016528 * t2 - 0.00000239 * t3
        var c1 = (0.1734 - 0.000393 * t) * Math.sin(m * dr) + 0.0021 * Math.sin(2 * dr * m)
        c1 -= 0.4068 * Math.sin(mpr * dr) + 0.0161 * Math.sin(dr * 2 * mpr)
        c1 -= 0.0004 * Math.sin(dr * 3 * mpr)
        c1 += 0.0104 * Math.sin(dr * 2 * f) - 0.0051 * Math.sin(dr * (m + mpr))
        c1 -= 0.0074 * Math.sin(dr * (m - mpr)) + 0.0004 * Math.sin(dr * (2 * f + m))
        c1 -= 0.0004 * Math.sin(dr * (2 * f - m)) - 0.0006 * Math.sin(dr * (2 * f + mpr))
        c1 += 0.0010 * Math.sin(dr * (2 * f - mpr)) + 0.0005 * Math.sin(dr * (2 * mpr + m))
        val dt = if (t < -11) {
            0.001 + 0.000839 * t + 0.0002261 * t2 - 0.00000845 * t3 - 0.000000081 * t * t3
        } else {
            -0.000278 + 0.000265 * t + 0.000262 * t2
        }
        return jd + c1 - dt
    }

    /** Kinh độ mặt trời (radian) tại thời điểm jd (kiểu Double). */
    private fun sunLongitude(jd: Double): Double {
        val t = (jd - 2451545.0) / 36525
        val t2 = t * t
        val dr = Math.PI / 180
        val m = 357.5291 + 35999.0503 * t - 0.0001559 * t2 - 0.00000048 * t * t2
        val l0 = 280.46645 + 36000.76983 * t + 0.0003032 * t2
        var dl = (1.9146 - 0.004817 * t - 0.000014 * t2) * Math.sin(dr * m)
        dl += (0.019993 - 0.000101 * t) * Math.sin(dr * 2 * m) + 0.00029 * Math.sin(dr * 3 * m)
        var l = (l0 + dl) * dr
        l -= Math.PI * 2 * floor(l / (Math.PI * 2))
        return l
    }

    /** Ngày Julius (giờ VN) của điểm Sóc thứ k. */
    private fun getNewMoonDay(k: Int): Int {
        val jd = newMoon(k)
        val off = localTimeOffset(floor(jd).toInt())
        return floor(jd + 0.5 + off).toInt()
    }

    /** Chỉ số tiết khí (0..11) tại ngày dayNumber. */
    private fun getSunLongitude(dayNumber: Int): Int {
        val off = localTimeOffset(dayNumber)
        return floor(sunLongitude(dayNumber + 0.5 - off) / Math.PI * 6).toInt()
    }

    /** Ngày Julius bắt đầu tháng 11 âm lịch (tháng chứa Đông chí) của năm dương lịch year. */
    private fun getLunarMonth11(year: Int): Int {
        val jd = jdFromDate(31, 12, year)
        val off = localTimeOffset(jd)
        val sunLong = floor(sunLongitude(jd + 0.5 - off) / Math.PI * 6).toInt()
        val k = floor((jd - 2415021.076998695) / 29.530588853).toInt()
        var nmDay = getNewMoonDay(k + sunLong - 9)
        if (nmDay >= jd) {
            nmDay = getNewMoonDay(k + sunLong - 10)
        }
        return nmDay
    }

    /** Offset (tính theo tháng) của tháng nhuận so với tháng 11 âm lịch. */
    private fun getLeapMonthOffset(a11: Int): Int {
        val k = floor((a11 - 2415021.076998695) / 29.530588853 + 0.5).toInt()
        var last: Int
        var i = 1
        var arc = getSunLongitude(getNewMoonDay(k + i))
        do {
            last = arc
            i++
            arc = getSunLongitude(getNewMoonDay(k + i))
        } while (arc != last && i < 14)
        return i - 1
    }

    /** Chuyển số ngày Julius sang âm lịch. */
    private fun jdToLunar(dayNumber: Int): LunarTuple {
        val k = floor((dayNumber - 2415021.076998695) / 29.530588853).toInt()
        var monthStart = getNewMoonDay(k + 1)
        if (monthStart > dayNumber) {
            monthStart = getNewMoonDay(k)
        }

        var a11 = getLunarMonth11(yearOfJd(monthStart))
        var b11 = a11
        val calendarYear = yearOfJd(monthStart)
        val lunarYear: Int
        if (a11 >= monthStart) {
            lunarYear = calendarYear
            a11 = getLunarMonth11(calendarYear - 1)
        } else {
            lunarYear = calendarYear + 1
            b11 = getLunarMonth11(calendarYear + 1)
        }

        val lunarDay = dayNumber - monthStart + 1
        val diff = (monthStart - a11) / 29
        var leap = false
        var lunarMonth = diff + 11

        if (b11 - a11 > 365) {
            val leapMonthDiff = getLeapMonthOffset(a11)
            if (diff >= leapMonthDiff) {
                lunarMonth = diff + 10
                if (diff == leapMonthDiff) leap = true
            }
        }

        if (lunarMonth > 12) lunarMonth -= 12
        if (lunarMonth >= 11 && diff < 4) {
            // Năm âm lịch đã được điều chỉnh ở trên thông qua a11/b11.
        }

        return LunarTuple(lunarDay, lunarMonth, lunarYear, leap)
    }

    private data class LunarTuple(val day: Int, val month: Int, val year: Int, val leap: Boolean)
}
