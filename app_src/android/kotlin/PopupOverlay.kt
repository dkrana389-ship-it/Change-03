package com.deepinnovations.call_history

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PopupOverlay {
    private var rootView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var ticker: Runnable? = null

    private fun stopTicker() {
        ticker?.let { handler.removeCallbacks(it) }
        ticker = null
    }

    // Removes the popup immediately.
    fun dismiss(ctx: Context) {
        stopTicker()
        val v = rootView ?: return
        rootView = null
        try {
            val wm = ctx.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(v)
        } catch (_: Exception) {
        }
    }

    // Slides the card down, then removes the popup.
    private fun dismissAnimated(ctx: Context, root: View, card: View) {
        stopTicker()
        if (rootView !== root) return
        card.animate()
            .translationY(card.height.toFloat() + 300f)
            .setDuration(300)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                if (rootView === root) dismiss(ctx)
            }
            .start()
    }

    private fun fmt(sec: Long): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m ${s}s"
    }

    // Light red colour spreads from the centre like a drop.
    private fun reveal(tint: View) {
        tint.visibility = View.VISIBLE
        try {
            val cx = tint.width / 2
            val cy = tint.height / 2
            val radius = Math.hypot(cx.toDouble(), cy.toDouble()).toFloat()
            val anim = ViewAnimationUtils.createCircularReveal(tint, cx, cy, 0f, radius)
            anim.duration = 700
            anim.start()
        } catch (_: Exception) {
            tint.visibility = View.VISIBLE
        }
    }

    // Auto close 5 seconds after the data is visible.
    private fun startTimer(app: Context, root: View, card: View, timer: TextView) {
        var left = 5
        timer.text = "Auto close in ${left}s"
        val r = object : Runnable {
            override fun run() {
                if (rootView !== root) return
                left--
                if (left > 0) {
                    timer.text = "Auto close in ${left}s"
                    handler.postDelayed(this, 1000)
                } else {
                    dismissAnimated(app, root, card)
                }
            }
        }
        stopTicker()
        ticker = r
        handler.postDelayed(r, 1000)
    }

    @Suppress("DEPRECATION")
    fun show(
        ctx: Context,
        name: String?,
        number: String?,
        type: String,
        durSec: Long,
        today: CallStore.Today
    ) {
        val app = ctx.applicationContext
        if (!Settings.canDrawOverlays(app)) return
        dismiss(app)

        val dp = app.resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()
        fun tv(t: String, sp: Float, bold: Boolean, color: String): TextView {
            val x = TextView(app)
            x.text = t
            x.textSize = sp
            x.setTextColor(Color.parseColor(color))
            if (bold) x.setTypeface(x.typeface, Typeface.BOLD)
            return x
        }

        // Saved contact -> show name. Not saved -> show number.
        val nameText = name.orEmpty().trim()
        val numberText = number.orEmpty().trim()
        val title = when {
            nameText.isNotEmpty() -> nameText
            numberText.isNotEmpty() -> numberText
            else -> "Unknown number"
        }
        val subtitle = if (nameText.isNotEmpty() && numberText.isNotEmpty()) numberText else ""
        val date = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date())

        // ---- Card: white background, light-red layer on top of it, content on top of both
        val card = FrameLayout(app)
        val bg = GradientDrawable()
        bg.setColor(Color.WHITE)
        bg.cornerRadius = px(20).toFloat()
        card.background = bg
        card.clipToOutline = true
        card.elevation = px(8).toFloat()

        val tint = View(app)
        tint.setBackgroundColor(Color.parseColor("#FFCDD2"))
        tint.visibility = View.INVISIBLE
        // Height 0 on purpose: a plain View with MATCH_PARENT would stretch the card to full screen.
        // Its height is set to the content height right after layout (see listener below).
        card.addView(
            tint,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0)
        )

        val content = LinearLayout(app)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(px(18), px(16), px(18), px(16))
        content.alpha = 0f
        card.addView(
            content,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        content.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            val h = bottom - top
            val lp = tint.layoutParams
            if (h > 0 && lp.height != h) {
                lp.height = h
                tint.layoutParams = lp
            }
        }

        content.addView(tv("CALL ENDED", 12f, true, "#7A5560"))
        content.addView(tv(title, 24f, true, "#15233D"))
        if (subtitle.isNotEmpty()) content.addView(tv(subtitle, 14f, false, "#44546A"))
        content.addView(tv("$type  •  ${fmt(durSec)}  •  $date", 14f, false, "#44546A"))

        // Today's report
        val row = LinearLayout(app)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, px(14), 0, px(6))
        fun stat(value: String, label: String) {
            val col = LinearLayout(app)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER_HORIZONTAL
            col.addView(tv(value, 20f, true, "#15233D"))
            col.addView(tv(label, 12f, false, "#5B6B82"))
            row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        stat("${today.count}", "Calls today")
        stat("${today.missed}", "Missed")
        stat(fmt(today.totalSec), "Total talk")
        content.addView(row)

        val timer = tv("Auto close in 5s", 13f, true, "#B3261E")
        timer.setPadding(0, px(6), 0, px(6))
        content.addView(timer)

        // Ad placeholder
        val ad = tv("Ad space", 13f, false, "#8A94A6")
        ad.gravity = Gravity.CENTER
        val adBg = GradientDrawable()
        adBg.setColor(Color.parseColor("#99FFFFFF"))
        adBg.cornerRadius = px(8).toFloat()
        ad.background = adBg
        content.addView(ad, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(50)))

        val btns = LinearLayout(app)
        btns.orientation = LinearLayout.HORIZONTAL
        btns.setPadding(0, px(12), 0, 0)
        val close = Button(app)
        close.text = "Close"
        close.setOnClickListener { dismiss(app) }
        val callBack = Button(app)
        callBack.text = "Call Back"
        callBack.setOnClickListener {
            dismiss(app)
            if (numberText.isNotEmpty()) {
                val i = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(numberText)))
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                app.startActivity(i)
            }
        }
        val lp1 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lp1.marginEnd = px(6)
        val lp2 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lp2.marginStart = px(6)
        btns.addView(close, lp1)
        btns.addView(callBack, lp2)
        content.addView(btns)

        val root = FrameLayout(app)
        root.setPadding(px(12), px(12), px(12), px(24))
        root.addView(
            card,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        val windowType = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.BOTTOM

        // Start below the screen, then slide up.
        card.translationY = px(700).toFloat()
        try {
            val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.addView(root, params)
            rootView = root
        } catch (_: Exception) {
            return
        }

        card.animate()
            .translationY(0f)
            .setDuration(450)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                if (rootView === root) {
                    // 1) white card is up -> 2) data fades in -> 3) red drop spreads + 5s timer starts
                    content.animate()
                        .alpha(1f)
                        .setDuration(250)
                        .withEndAction {
                            if (rootView === root) {
                                reveal(tint)
                                startTimer(app, root, card, timer)
                            }
                        }
                        .start()
                }
            }
            .start()
    }
}
