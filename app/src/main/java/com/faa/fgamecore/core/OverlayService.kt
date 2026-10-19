package com.faa.fgamecore.core

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.faa.fgamecore.MainActivity
import com.faa.fgamecore.ui.BorderC
import com.faa.fgamecore.ui.CardBg
import com.faa.fgamecore.ui.Cyan
import com.faa.fgamecore.ui.Green
import com.faa.fgamecore.ui.Muted
import com.faa.fgamecore.ui.Red
import com.faa.fgamecore.ui.TextMain
import com.faa.fgamecore.ui.Yellow
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Floating FGC status panel. Plain [WindowManager] overlay over other apps; it reads
 * the same [Fgc] state the daemon updates, so it never polls on its own.
 */
class OverlayService : LifecycleService() {

    private lateinit var wm: WindowManager
    private var panel: View? = null
    private var bubble: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var minimized = false
    private var torn = false

    private var gameText: TextView? = null
    private var profileText: TextView? = null
    private var shizukuText: TextView? = null
    private var fgcText: TextView? = null
    private var noteText: TextView? = null
    private var chips: View? = null
    private var profileButton: TextView? = null
    private var profileRow: View? = null
    private var shizukuRow: View? = null
    private var fgcRow: View? = null

    override fun onCreate() {
        super.onCreate()
        Fgc.init(applicationContext)
        Notifier.ensureChannels(this)
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        ServiceCompat.startForeground(
            this, NOTIF_ID, Notifier.overlay(this),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        if (!allowed(this)) {
            Logs.add("OVERLAY_ERROR", "Izin overlay tidak ada saat service start")
            stopSelf()
            return
        }
        Fgc.markOverlay(true)
        minimized = Fgc.store.overlayMinimized
        if (minimized) attachBubble() else attachPanel()
        lifecycleScope.launch { Fgc.state.collect { render(it) } }
        Logs.add("OVERLAY", "Overlay tampil (${if (minimized) "bubble" else "panel"})")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_CLOSE -> close()
            ACTION_SHOW -> if (torn.not()) { minimized = false; attachPanel() }
        }
        return START_NOT_STICKY // a user stop must stay stopped; no ghost bubble
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rotation / resize: re-clamp so the window never lands off-screen.
        panelParams?.let { clamp(it, panel) }
        bubbleParams?.let { clamp(it, bubble) }
    }

    override fun onDestroy() {
        teardown()
        Fgc.markOverlay(false)
        Logs.add("OVERLAY", "Overlay dihentikan")
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    // --- windows ---

    private fun attachPanel() {
        if (torn) return
        if (!allowed(this)) {
            Logs.add("OVERLAY_ERROR", "Izin overlay dicabut, panel dibersihkan")
            close()
            return
        }
        detachBubble()
        if (panel != null) return
        val p = panelParams ?: windowParams(dp(PANEL_DP), WRAP).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (Fgc.store.overlayX >= 0) Fgc.store.overlayX else dp(12)
            y = if (Fgc.store.overlayY >= 0) Fgc.store.overlayY else dp(120)
        }
        val v = buildPanel()
        runCatching { wm.addView(v, p) }.onFailure {
            Logs.add("OVERLAY_ERROR", "addView panel gagal: ${it.message}")
            note("Overlay gagal ditambahkan")
            stopSelf()
            return
        }
        panel = v
        panelParams = p
        clamp(p, v)
        render(Fgc.state.value)
    }

    private fun attachBubble() {
        if (torn) return
        if (!allowed(this)) {
            Logs.add("OVERLAY_ERROR", "Izin overlay dicabut, bubble dibersihkan")
            close()
            return
        }
        detachPanel()
        if (bubble != null) return
        val p = bubbleParams ?: windowParams(dp(56), dp(56)).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (Fgc.store.overlayX >= 0) Fgc.store.overlayX else dp(12)
            y = if (Fgc.store.overlayY >= 0) Fgc.store.overlayY else dp(120)
        }
        val v = buildBubble()
        runCatching { wm.addView(v, p) }.onFailure {
            Logs.add("OVERLAY_ERROR", "addView bubble gagal: ${it.message}")
            stopSelf()
            return
        }
        bubble = v
        bubbleParams = p
        clamp(p, v)
    }

    private fun detachPanel() {
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null
    }

    private fun detachBubble() {
        bubble?.let { runCatching { wm.removeView(it) } }
        bubble = null
    }

    private fun teardown() {
        if (torn) return
        torn = true
        detachPanel()
        detachBubble()
    }

    private fun close() {
        // A user close must also flip the setting, or Settings would claim it is running.
        Fgc.store.overlayEnabled = false
        teardown()
        stopSelf()
    }

    private fun minimize() {
        Fgc.store.overlayMinimized = true
        minimized = true
        attachBubble()
    }

    private fun expand() {
        Fgc.store.overlayMinimized = false
        minimized = false
        attachPanel()
    }

    // --- views ---

    @SuppressLint("ClickableViewAccessibility")
    private fun buildPanel(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(CardBg.toArgb())
                setStroke(dp(1), BorderC.toArgb())
            }
            layoutParams = LinearLayout.LayoutParams(dp(PANEL_DP), WRAP)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(6), dp(8))
        }
        header.addView(text("Faa GameCore", 12f, Cyan.toArgb(), bold = true))
        header.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
        header.addView(iconButton("—") { minimize() })
        header.addView(iconButton("×") { close() })
        root.addView(header)

        root.addView(divider())

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        gameText = row(body, "Game").second
        profileText = row(body, "Profile").also { profileRow = it.first }.second
        shizukuText = row(body, "Shizuku").also { shizukuRow = it.first }.second
        fgcText = row(body, "Faa GameCore").also { fgcRow = it.first }.second
        noteText = text("", 10f, Muted.toArgb()).apply {
            setPadding(0, dp(4), 0, 0)
            visibility = View.GONE
        }
        body.addView(noteText)
        root.addView(body)

        chips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(10), 0, dp(10), dp(8))
            visibility = View.GONE
            listOf(1, 2, 3, 4).forEach { p ->
                addView(chip(Policy.profileName(p)) { applyProfile(p) })
            }
        }
        root.addView(chips)

        root.addView(divider())

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        profileButton = actionButton("PROFILE") {
            chips?.visibility = if (chips?.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        actions.addView(profileButton)
        actions.addView(actionButton("LOGS") { openApp(logs = true) })
        actions.addView(actionButton("HOME") { openApp(logs = false) })
        root.addView(actions)

        header.setOnTouchListener(dragListener(::panelDragParams) { savePosition() })
        return root
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildBubble(): View {
        val v = TextView(this).apply {
            text = "Faa"
            setTextColor(Cyan.toArgb())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(CardBg.toArgb())
                setStroke(dp(2), Cyan.toArgb())
            }
        }
        v.setOnTouchListener(dragListener(::bubbleDragParams, onTap = { expand() }) { savePosition() })
        return v
    }

    private fun row(parent: LinearLayout, label: String): Pair<LinearLayout, TextView> {
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), 0, dp(2))
        }
        line.addView(text(label, 10.5f, Muted.toArgb()))
        line.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(8), 1) })
        val value = text("—", 10.5f, TextMain.toArgb(), bold = true)
        line.addView(value)
        parent.addView(line)
        return line to value
    }

    private fun text(s: String, sp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }

    private fun iconButton(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        setTextColor(Muted.toArgb())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
        setOnClickListener { onClick() }
    }

    private fun actionButton(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        setTextColor(Cyan.toArgb())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f)
        setOnClickListener { onClick() }
    }

    private fun chip(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        setTextColor(TextMain.toArgb())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
        gravity = Gravity.CENTER
        maxLines = 1
        layoutParams = LinearLayout.LayoutParams(0, dp(28), 1f).apply { marginEnd = dp(4) }
        background = GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
            setColor(tint(0.10f))
            setStroke(dp(1), BorderC.toArgb())
        }
        setOnClickListener { onClick() }
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(BorderC.toArgb())
        layoutParams = LinearLayout.LayoutParams(MATCH, dp(1))
    }

    // --- state ---

    private fun render(s: UiSnapshot) {
        if (torn) return
        if (!allowed(this)) {
            Logs.add("OVERLAY_ERROR", "Izin overlay dicabut saat aktif")
            close()
            return
        }
        val granted = s.shizuku == ShizukuStatus.Granted
        profileRow?.visibility = if (Fgc.store.overlayShowProfile) View.VISIBLE else View.GONE
        shizukuRow?.visibility = if (Fgc.store.overlayShowShizuku) View.VISIBLE else View.GONE
        fgcRow?.visibility = if (Fgc.store.overlayShowStatus) View.VISIBLE else View.GONE
        gameText?.text = s.session?.game?.let { Device.label(this, it) } ?: "—"
        profileText?.text = Policy.profileName(s.session?.profile ?: Fgc.store.defaultProfile)
        shizukuText?.apply {
            text = if (granted) "Connected" else "Offline"
            setTextColor((if (granted) Green else Red).toArgb())
        }
        fgcText?.apply {
            text = when {
                s.overlay && s.daemon -> "Active"
                s.overlay -> "Overlay only"
                else -> "Inactive"
            }
            setTextColor((if (s.daemon) Green else Yellow).toArgb())
        }
        profileButton?.apply {
            isEnabled = true
            alpha = if (granted) 1f else 0.45f
            setTextColor(if (granted) Cyan.toArgb() else Muted.toArgb())
        }
        if (s.session == null) chips?.visibility = View.GONE
    }

    private fun note(msg: String) {
        noteText?.apply {
            text = msg
            setTextColor(Yellow.toArgb())
            visibility = View.VISIBLE
        }
        Logs.add("OVERLAY", msg)
    }

    private fun applyProfile(p: Int) {
        lifecycleScope.launch {
            val err = Fgc.changeProfile(p)
            if (err != null) note("Profil gagal: $err") else {
                note("Profil -> ${Policy.profileName(p)}")
                chips?.visibility = View.GONE
            }
        }
    }

    private fun openApp(logs: Boolean) {
        val i = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (logs) putExtra(MainActivity.EXTRA_OPEN_LOGS, true)
        }
        runCatching { startActivity(i) }
        // The overlay stays up: the user may come straight back to the game.
    }

    // --- window plumbing ---

    private fun windowParams(w: Int, h: Int) = WindowManager.LayoutParams(
        w, h,
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        android.graphics.PixelFormat.TRANSLUCENT,
    )

    private fun panelDragParams(): Pair<WindowManager.LayoutParams, View?>? =
        panelParams?.let { it to panel }

    private fun bubbleDragParams(): Pair<WindowManager.LayoutParams, View?>? =
        bubbleParams?.let { it to bubble }

    private fun savePosition() {
        val p = if (minimized) bubbleParams else panelParams
        if (p != null) {
            Fgc.store.overlayX = p.x
            Fgc.store.overlayY = p.y
        }
    }

    private fun clamp(p: WindowManager.LayoutParams, v: View?) {
        val screen = resources.displayMetrics
        val w = v?.width?.takeIf { it > 0 } ?: dp(PANEL_DP)
        val h = v?.height?.takeIf { it > 0 } ?: dp(56)
        p.x = p.x.coerceIn(0, (screen.widthPixels - w).coerceAtLeast(0))
        p.y = p.y.coerceIn(0, (screen.heightPixels - h).coerceAtLeast(0))
        v?.let { runCatching { wm.updateViewLayout(it, p) } }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun dragListener(
        params: () -> Pair<WindowManager.LayoutParams, View?>?,
        onTap: (() -> Unit)? = null,
        onMoved: () -> Unit = {},
    ) = View.OnTouchListener { _, e ->
        val pair = params() ?: return@OnTouchListener false
        val (p, v) = pair
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX
                downY = e.rawY
                startX = p.x
                startY = p.y
                moved = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - downX
                val dy = e.rawY - downY
                if (!moved && abs(dx) + abs(dy) > dp(6)) moved = true
                if (moved) {
                    p.x = startX + dx.toInt()
                    p.y = startY + dy.toInt()
                    clamp(p, v)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (moved) onMoved() else onTap?.invoke()
            }
        }
        true
    }

    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var moved = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun tint(alpha: Float) = Cyan.copy(alpha = alpha).toArgb()

    companion object {
        const val NOTIF_ID = 1002
        const val ACTION_CLOSE = "com.faa.fgamecore.OVERLAY_CLOSE"
        const val ACTION_SHOW = "com.faa.fgamecore.OVERLAY_SHOW"

        /** Panel width: a compact card, never more than half a small screen. */
        private const val PANEL_DP = 232
        private const val WRAP = WindowManager.LayoutParams.WRAP_CONTENT
        private const val MATCH = WindowManager.LayoutParams.MATCH_PARENT

        /** Overlay permission is the only gate; no Accessibility service is used. */
        fun allowed(ctx: Context): Boolean = runCatching { Settings.canDrawOverlays(ctx) }
            .getOrDefault(false)
    }
}
