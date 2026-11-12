package com.ntstudio.clock

import android.animation.TimeAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Đồng hồ số dạng Odometer với hiệu ứng spring physics.
 *
 * Cảm hứng từ kỹ thuật của Morphicons (guillermolg00/morphicons): thay vì đổi số
 * ngay lập tức, mỗi chữ số được "kéo" tới giá trị đích bằng một hệ lò xo (spring) có
 * damping — tạo cảm giác chuyển động tự nhiên, hơi overshoot rồi ổn định.
 *
 * Với mỗi khe chữ số, ta lưu một "vị trí ảo" [DigitSlot.value] (số thực). Khi giá trị
 * đích thay đổi (ví dụ 5 → 6), lò xo kéo value từ 5 tới 6. Giá trị lẻ (0.37) nghĩa là
 * đang nằm giữa hai chữ số → vẽ 2 chữ số liền nhau, chữ cũ trượt lên và mờ dần, chữ mới
 * trượt vào từ dưới — tạo cảm giác cuộn mượt như odometer.
 *
 * Định dạng hiển thị: HH:MM:SS
 */
class OdometerClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** Một khe chữ số trong chuỗi HH:MM:SS. */
    private class DigitSlot(
        /** Độ cứng lò xo riêng — khe đổi càng thưa thì càng nhỏ (chậm, mượt hơn). */
        val stiffness: Float,
        /** Tỉ lệ giảm chấn riêng. */
        val damping: Float
    ) {
        /** Giá trị hiển thị hiện tại (số thực, có thể lẻ khi đang chuyển động). */
        var value: Float = 0f

        /** Vận tốc hiện tại của lò xo. */
        var velocity: Float = 0f

        /** Giá trị đích. */
        var target: Float = 0f
    }

    // ---------------------------------------------------------------------
    // Cấu hình
    // ---------------------------------------------------------------------

    /*
     * Mỗi khe có một hệ lò xo RIÊNG, phù hợp với nhịp đổi của nó:
     *
     *   slot 0,1 (giờ)        — đổi 1 lần/giờ      → rất chậm, rất mượt
     *   slot 2,3 (phút)       — đổi 1 lần/phút     → chậm vừa
     *   slot 4   (chục giây)  — đổi 1 lần/10 giây  → nhanh vừa
     *   slot 5   (đơn vị giây)— đổi 1 lần/giây     → nhanh nhất, phải xong <1s
     *
     * Trước đây mọi khe dùng chung một stiffness nên hàng chục giây / phút / giờ
     * "bật" rất nhanh sau một quãng chờ dài, trông giật.
     */
    /*
     * Tốc độ được chọn theo hướng "thư thái": khe càng ít đổi thì chuyển
     * càng chậm, tạo cảm giác êm và có chiều sâu. Mọi bộ đều 0% overshoot.
     *
     * Settling time đo được (60fps):
     *   giờ   ~1.33s   | phút chục ~1.18s | phút đơn vị ~1.10s
     *   giây chục ~0.92s | giây đơn vị ~0.55s (< 1s bắt buộc)
     */
    private val slots = arrayOf(
        // Giờ - chục & đơn vị: đổi 1 lần/giờ → rất chậm, rất êm.
        DigitSlot(stiffness = 55f, damping = 0.96f),
        DigitSlot(stiffness = 55f, damping = 0.96f),
        // Phút - chục: đổi 1 lần/10 phút → chậm.
        DigitSlot(stiffness = 70f, damping = 0.95f),
        // Phút - đơn vị: đổi 1 lần/phút → chậm vừa.
        DigitSlot(stiffness = 85f, damping = 0.95f),
        // Giây - chục: đổi 1 lần/10 giây → nhịp vừa.
        DigitSlot(stiffness = 130f, damping = 0.94f),
        // Giây - đơn vị: đổi mỗi giây → phải xong <1s.
        DigitSlot(stiffness = 520f, damping = 0.93f)
    )

    /**
     * Bố cục chuỗi hiển thị: chỉ số khe chữ số (0..5), hoặc -1 là dấu ':'.
     * => "H H : M M : S S"
     */
    private val layout = intArrayOf(0, 1, -1, 2, 3, -1, 4, 5)

    /** Chỉ số khe bắt đầu của nhóm giây (dùng để tô màu đỏ). */
    private val secondsSlotStart = 4

    // ---------------------------------------------------------------------
    // Tham số lò xo (spring)
    // ---------------------------------------------------------------------

    /*
     * Tham số lò xo nằm trong từng [DigitSlot] (xem khai báo slots).
     *
     * Ràng buộc bắt buộc: khe đơn vị giây PHẢI dừng trong < 1 giây vì
     * refresh() gọi mỗi giây. Các khe còn lại đổi thưa hơn nên được phép chậm
     * hơn nhiều → chuyển động êm và "đã" hơn.
     */

    /** Càng lên cao càng dừng sớm (nhưng vẫn phải mượt). */
    private val restThreshold = 0.004f

    // ---------------------------------------------------------------------
    // Paints
    // ---------------------------------------------------------------------

    private val digitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.clock2_text_primary)
        textAlign = Paint.Align.CENTER
        // SVN-Gill Sans Bold — số in đậm.
        typeface = ResourcesCompat.getFont(context, R.font.svn_gill_bold)
            ?: Typeface.DEFAULT_BOLD
    }

    private val colonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.clock2_accent)
        textAlign = Paint.Align.CENTER
        typeface = ResourcesCompat.getFont(context, R.font.svn_gill_bold)
            ?: Typeface.DEFAULT_BOLD
    }

    /** Số giây — màu đỏ. */
    private val secondsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.clock2_seconds)
        textAlign = Paint.Align.CENTER
        typeface = ResourcesCompat.getFont(context, R.font.svn_gill_bold)
            ?: Typeface.DEFAULT_BOLD
    }

    // ---------------------------------------------------------------------
    // Hình học
    // ---------------------------------------------------------------------

    private var digitWidth = 0f
    private var colonWidth = 0f
    private var totalWidth = 0f
    private var baseline = 0f

    private val digitMetrics = Paint.FontMetrics()

    // ---------------------------------------------------------------------
    // Vòng lặp animation
    // ---------------------------------------------------------------------

    private val animator = TimeAnimator().apply {
        setTimeListener { _, _, deltaMs ->
            if (deltaMs > 0) {
                stepSprings((deltaMs / 1000f).coerceAtMost(0.05f))
                invalidate()
            }
        }
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        syncTargets(force = true)
    }

    // ---------------------------------------------------------------------
    // Vòng đời
    // ---------------------------------------------------------------------

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animator.isStarted) animator.start()
    }

    override fun onDetachedFromWindow() {
        if (animator.isStarted) animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeGeometry(w, h)
    }

    /**
     * Tính cỡ chữ lớn nhất vừa khung, cho cả bề ngang và bề cao.
     *
     * Đo bề rộng thật của từng chữ số (0..9) rồi lấy giá trị lớn nhất — Gill Sans Bold
     * là font tỉ lệ (proportional) nên "1" hẹp hơn "8" rất nhiều; dùng measureText("0")
     * sẽ khiến chuỗi bị tính sai và tràn khung.
     */
    private fun computeGeometry(w: Int, h: Int) {
        // Chữ số cao tối đa 84% chiều cao view (đã bỏ nhãn nên có thể dùng nhiều hơn).
        val maxTextHeight = h * 0.84f

        // --- Bước 1: đo ở cỡ chuẩn 100 để lấy tỉ lệ ---
        digitPaint.textSize = 100f
        colonPaint.textSize = 100f
        digitPaint.getFontMetrics(digitMetrics)
        val heightRatio = (digitMetrics.descent - digitMetrics.ascent) / 100f

        var maxDigitWidthRatio = 0f
        for (d in 0..9) {
            val ratio = digitPaint.measureText(d.toString()) / 100f
            if (ratio > maxDigitWidthRatio) maxDigitWidthRatio = ratio
        }
        val colonWidthRatio = colonPaint.measureText(":") / 100f

        // --- Bước 2: chọn cỡ chữ nhỏ hơn giữa ràng buộc ngang và dọc ---
        val widthUnits = 6 * maxDigitWidthRatio + 2 * colonWidthRatio
        // Giữ lề an toàn hai bên trên TV (overscan) — dùng 90% bề ngang khả dụng.
        val sizeByWidth = (w * 0.90f) / widthUnits
        val sizeByHeight = maxTextHeight / heightRatio
        var textSize = minOf(sizeByWidth, sizeByHeight)

        digitPaint.textSize = textSize
        colonPaint.textSize = textSize

        // --- Bước 3: đo lại Ở ĐÚNG CỠ CHỮ CUỐI CÙNG ---
        // Font hinting khiến bề rộng thực tế khác với tỉ lệ đo ở cỡ 100,
        // nên phải đo lại rồi thu nhỏ nếu vẫn tràn.
        val maxAvailableWidth = w * 0.92f
        var maxDigitWidth: Float
        var realColonWidth: Float

        // Lặp tối đa 3 lần để hội tụ: mỗi lần thu nhỏ thì bề rộng thực lại đổi chút.
        repeat(3) {
            var widest = 0f
            for (d in 0..9) {
                val dw = digitPaint.measureText(d.toString())
                if (dw > widest) widest = dw
            }
            val cw = colonPaint.measureText(":")
            val total = 6 * widest + 2 * cw
            if (total <= maxAvailableWidth) return@repeat
            textSize *= maxAvailableWidth / total
            digitPaint.textSize = textSize
            colonPaint.textSize = textSize
        }

        maxDigitWidth = 0f
        for (d in 0..9) {
            val dw = digitPaint.measureText(d.toString())
            if (dw > maxDigitWidth) maxDigitWidth = dw
        }
        realColonWidth = colonPaint.measureText(":")

        secondsPaint.textSize = textSize
        digitPaint.getFontMetrics(digitMetrics)

        // Căn giữa dọc theo chiều cao view.
        baseline = (h - (digitMetrics.descent - digitMetrics.ascent)) / 2f -
            digitMetrics.ascent

        // Ô chữ số dùng bề rộng CỐ ĐỊNH = bề rộng chữ số rộng nhất (không cộng gap)
        // để 6 ô chữ số + 2 dấu ':' luôn vừa khung đã tính ở vòng lặp scale.
        digitWidth = maxDigitWidth
        colonWidth = realColonWidth
        totalWidth = 6 * digitWidth + 2 * colonWidth
    }

    // ---------------------------------------------------------------------
    // Vẽ
    // ---------------------------------------------------------------------

    private var frameNo = 0L

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (digitWidth <= 0f) return

        // ==== DEBUG: ghi lại trạng thái từng frame của khe GIÂY đơn vị (slot 5)
        if (debugEnabled) {
            frameNo++
            val s = slots[5]
            val sh = floor(s.value).toInt() % 10
            if (sh != lastLoggedShown[5] || frameNo % 30L == 0L) {
                dbg(
                    "FRAME#$frameNo slot5 value=${f(s.value)} target=${f(s.target)} " +
                        "vel=${f(s.velocity)} shown=$sh"
                )
            }
        }

        // Căn giữa khối số trong view, có chừa lề an toàn 2 bên (overscan TV).
        val safeInset = width * 0.02f
        val availableWidth = width - 2 * safeInset
        var x = safeInset + (availableWidth - totalWidth) / 2f

        for (item in layout) {
            if (item < 0) {
                canvas.drawText(":", x + colonWidth / 2f, baseline, colonPaint)
                x += colonWidth
            } else {
                // Nhóm giây (khe 4, 5) tô màu đỏ; giờ & phút màu trắng.
                val paint = if (item >= secondsSlotStart) secondsPaint else digitPaint
                drawSlot(canvas, slots[item], x + digitWidth / 2f, paint)
                x += digitWidth
            }
        }
    }

    /**
     * Vẽ một khe chữ số đang ở vị trí [DigitSlot.value] (số thực).
     * Phần lẻ quyết định độ "cuộn" giữa hai chữ số liền nhau.
     */
    private fun drawSlot(canvas: Canvas, slot: DigitSlot, centerX: Float, paint: Paint) {
        // Cửa sổ cuộn của từng ô: cao đúng một dòng chữ (ascent..descent) cộng thêm
        // một chút đệm để không cắt đỉnh/chân chữ trong lúc chuyển động.
        /*
         * Cửa sổ cuộn: cao đúng MỘT dòng chữ (đo bằng ascent..descent của font).
         *
         * Trước đây dùng cellHeight * 0.10 làm đệm → khi chữ mới trượt vào từ
         * dưới, phần đỉnh của nó nằm ngoài khung clip nên bị cắt ngọn, trông
         * như chữ "nhỏ đi" rồi mới "bật" lên đủ cỡ. Đó chính là cảm giác
         * chớp mà mắt thấy.
         *
         * Dùng đúng ascent..descent thì chữ luôn được vẽ trọn vẹn trong khung
         * của nó, và chỉ có 1 chữ hiện tại + 1 chữ kế tiếp ló ra khi cuộn.
         */
        val cellTop = baseline + digitMetrics.ascent
        val cellBottom = baseline + digitMetrics.descent
        val centerY = (cellTop + cellBottom) / 2f
        /*
         * Khung clip cao đúng MỘT ô chữ (ascent..descent).
         *
         * Chữ hiện tại được vẽ ở baseline, chữ kế tiếp ở baseline + stepHeight,
         * trong đó stepHeight = descent - ascent = chiều cao ô. Nhờ vậy khi
         * fraction = 0.5, đúng một nửa mỗi chữ nằm trong khung — giống odometer.
         * Cộng thêm 1px đệm để font hinting không bị cắt nét.
         */
        val halfHeight = (cellBottom - cellTop) / 2f + 1f

        // Nới rộng cửa sổ clip thêm một chút để không cắt chân/đỉnh chữ số.
        val clipHalfWidth = digitWidth / 2f + paint.textSize * 0.08f
        canvas.save()
        canvas.clipRect(
            centerX - clipHalfWidth,
            centerY - halfHeight,
            centerX + clipHalfWidth,
            centerY + halfHeight
        )
        drawSlotUnclipped(canvas, slot, centerX, paint)
        canvas.restore()
    }

    private fun drawSlotUnclipped(canvas: Canvas, slot: DigitSlot, centerX: Float, paint: Paint) {
        val v = slot.value
        val lower = floor(v).toInt()

        /*
         * fraction = phần lẻ của value, quyết định chữ đang cuộn được bao nhiêu.
         *
         * KHÔNG clamp về 0 khi fraction sát 1. Làm vậy (fraction > 0.996 -> 0)
         * khiến frame cuối cùng của bước cuộn nhảy về chữ số CŨ với alpha đầy
         * đủ — đó chính là cú "chớp lùi 1 số rồi chớp lên" đã báo.
         *
         * alpha tự nhiên về 0 khi fraction -> 1 nên không cần clamp.
         */
        val fraction = v - lower

        val lowerDigit = ((lower % 10) + 10) % 10
        val upperDigit = (((lower + 1) % 10) + 10) % 10

        /*
         * Bước cuộn PHẢI đúng bằng chiều cao của cửa sổ clip, không phải
         * (descent - ascent).
         *
         * Gill Sans Bold có ascent lớn nhưng descent rất nhỏ, nên
         * (descent - ascent) ≠ chiều cao khung nhìn thấy được. Dùng sai giá trị
         * khiến chữ mới lệch khỏi ô của nó → mắt thấy chữ bị "tụt xuống" rồi
         * bật lên khi cuộn xong.
         *. Ở đây dùng đúng cellHeight giống hệt drawSlot().
         */
        val cellTop = baseline + digitMetrics.ascent
        val cellBottom = baseline + digitMetrics.descent
        val stepHeight = cellBottom - cellTop

        // Chữ cũ trượt lên và mờ dần; chữ mới trượt vào từ dưới.
        val alphaLower = ((1f - fraction) * 255f).roundToInt().coerceIn(0, 255)
        val alphaUpper = (fraction * 255f).roundToInt().coerceIn(0, 255)

        if (alphaLower > 0) {
            paint.alpha = alphaLower
            canvas.drawText(
                lowerDigit.toString(),
                centerX,
                baseline - fraction * stepHeight,
                paint
            )
        }
        if (alphaUpper > 0) {
            paint.alpha = alphaUpper
            canvas.drawText(
                upperDigit.toString(),
                centerX,
                baseline + (1f - fraction) * stepHeight,
                paint
            )
        }
        paint.alpha = 255
    }

    // ---------------------------------------------------------------------
    // Đồng bộ giá trị & lò xo
    // ---------------------------------------------------------------------

    private var lastHour = -1
    private var lastMinute = -1
    private var lastSecond = -1

    /**
     * Đọc giờ hệ thống. Nếu có thay đổi (hoặc [force]) thì cập nhật target cho các khe.
     * Gọi hàm này mỗi giây từ bên ngoài (hoặc để onDraw tự gọi nếu cần).
     */
    fun refresh() {
        syncTargets(force = false)
    }

    private fun syncTargets(force: Boolean) {
        val cal = Calendar.getInstance()
        if (debugEnabled && !force) {
            // Cảnh báo nếu lò xo chưa dừng khi tick mới tới — đây là nguồn gốc
            // của hiện tượng giật/chớp.
            for (i in slots.indices) {
                val d = abs(slots[i].target - slots[i].value)
                if (d > 0.01f) {
                    dbg(
                        "!!! NOT SETTLED slot=$i value=${f(slots[i].value)} " +
                            "target=${f(slots[i].target)} diff=${f(d)}"
                    )
                }
            }
        }
        applyTime(
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            cal.get(Calendar.SECOND),
            force
        )
    }

    /**
     * Bật để ghi log chẩn đoán hiệu ứng cuộn (regression, lò xo chưa dừng).
     * Mặc định tắt ở bản phát hành.
     */
    private val debugEnabled = false

    private fun dbg(msg: String) {
        if (debugEnabled) android.util.Log.d("OdoDbg", msg)
    }

    private val lastLoggedShown = IntArray(6) { -1 }

    private fun applyTime(h: Int, m: Int, s: Int, force: Boolean = false) {
        if (!force && h == lastHour && m == lastMinute && s == lastSecond) return
        lastHour = h
        lastMinute = m
        lastSecond = s

        val values = intArrayOf(h / 10, h % 10, m / 10, m % 10, s / 10, s % 10)

        for (i in slots.indices) {
            val slot = slots[i]
            val newTarget = values[i]

            if (force) {
                // Lần đầu: đặt ngay, không chạy animation từ 0.
                slot.target = newTarget.toFloat()
                slot.value = newTarget.toFloat()
                slot.velocity = 0f
                continue
            }

            /*
             * Điểm mấu chốt: dùng **target** (đích đã đặt) làm gốc, KHÔNG dùng
             * value (vị trí đang chạy dở).
             *
             * Lý do: nếu lò xo chưa kịp dừng mà tick kế tiếp đã tới (value =
             * 7.947 trong khi target = 8), việc tính từ value sẽ cho base = 7 và
             * cộng thêm bước, làm target bị đẩy lùi/đổi hướng giữa chừng — đó
             * chính là hiện tượng "chớp lùi rồi chớp lên".
             *
             * Lấy target làm gốc thì dù animation có đang chạy hay đã xong, đích
             * vẫn luôn tịnh tiến đơn điệu: 8 → 9 → 10 … không bao giờ quay lại.
             */
            val base = floor(slot.target)
            val targetDigit = (base.toInt()) % 10
            if (newTarget == targetDigit) continue

            // Số bước dương ngắn nhất từ chữ số đích hiện tại tới chữ số mới.
            val steps = (newTarget - targetDigit + 10) % 10

            val oldTarget = slot.target
            slot.target = base + steps

            dbg("SYNC slot=$i value=${f(slot.value)} targetDigit=$targetDigit new=$newTarget " +
                "base=${f(base)} steps=$steps target ${f(oldTarget)} -> ${f(slot.target)}")
        }
    }

    private fun f(v: Float) = String.format(java.util.Locale.US, "%.3f", v)

    /**
     * Tích phân hệ lò xo giảm chấn một bước [dt] giây (semi-implicit Euler):
     *
     *   a = k * (target - x) - c * v
     *   v += a * dt
     *   x += v * dt
     *
     * value/target tăng vô hạn theo thời gian; việc quy về chữ số 0..9 do phép
     * % 10 đảm nhận lúc vẽ. Không có bước chuẩn hoá nào chen vào giữa animation
     * nên hình ảnh luôn cuộn liền mạch.
     */
    private fun stepSprings(dt: Float) {
        for (slot in slots) {
            // Mỗi khe có bộ tham số riêng (xem khai báo [slots]).
            val k = slot.stiffness
            // Hệ số giảm chấn: c = 2 * zeta * omega, với omega = sqrt(k)
            val c = 2f * slot.damping * kotlin.math.sqrt(k)

            val displacement = slot.target - slot.value
            val accel = k * displacement - c * slot.velocity
            slot.velocity += accel * dt
            slot.value += slot.velocity * dt

            if (abs(slot.target - slot.value) < restThreshold &&
                abs(slot.velocity) < restThreshold
            ) {
                // Chốt về đúng đích khi đã dừng hẳn (không dịch chuyển giá trị).
                slot.value = slot.target
                slot.velocity = 0f
            }
        }

        // DEBUG: phát hiện chữ số hiển thị bị ĐI LÙI (regression) và lò xo chưa dừng
        if (debugEnabled) {
            for (idx in slots.indices) {
                val shown = floor(slots[idx].value).toInt() % 10
                val prev = lastLoggedShown[idx]
                if (prev != -1 && shown != prev) {
                    val delta = ((shown - prev) % 10 + 10) % 10
                    if (delta != 1) {
                        dbg("!!! REGRESSION slot=$idx $prev -> $shown " +
                            "(delta=$delta) value=${f(slots[idx].value)} " +
                            "target=${f(slots[idx].target)}")
                    }
                }
                lastLoggedShown[idx] = shown
            }
        }

        /*
         * KHÔNG chuẩn hoá (không trừ 10) ở đây.
         *
         * Toàn bộ việc "về 0..9" đã được xử lý bằng phép % 10 lúc VẼ
         * (xem drawSlot: lowerDigit = floor(value) % 10).
         * value/target cứ tăng vô hạn; float 32-bit đủ chính xác cho hàng triệu
         * bước cuộn nên không cần thu gọn.
         *
         * Trước đây code có normalizeIfIdle() trừ 10 khi lò xo dừng — chính nó gây
         * "chớp": NORMALIZE chạy ngay khi value vừa chạm 10.0 trong lúc animation
         * 8→9→0 mới đi được nửa đường (mới qua 9), làm mất hẳn đoạn cuộn qua 0.
         */
    }
}
