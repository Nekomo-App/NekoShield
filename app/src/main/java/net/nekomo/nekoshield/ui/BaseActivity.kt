package net.nekomo.nekoshield.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import net.nekomo.nekoshield.R

/** Shared screen chrome — every section gets the same dark, TV-ready scaffold. */
abstract class BaseActivity : AppCompatActivity() {

    protected lateinit var body: LinearLayout
        private set

    fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0B0E"))
        }
        val scroll = ScrollView(this)
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(28))
        }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        build()
    }

    abstract fun build()

    protected fun title(text: String): TextView {
        val t = TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
        }
        body.addView(t)
        return t
    }

    protected fun subtitle(text: String): TextView {
        val t = TextView(this).apply {
            this.text = text
            setTextColor(getColor(R.color.muted))
            textSize = 13f
            setPadding(0, dp(4), 0, dp(14))
        }
        body.addView(t)
        return t
    }

    protected fun section(text: String): TextView {
        val t = TextView(this).apply {
            this.text = text.uppercase()
            setTextColor(getColor(R.color.accent))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(20), 0, dp(8))
        }
        body.addView(t)
        return t
    }

    /** Standard tappable row — D-pad focusable with the violet focus ring. */
    protected fun row(title: String, sub: String = "", tag: String = "",
                      onClick: (() -> Unit)? = null): LinearLayout {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            isFocusable = true
            isClickable = true
            background = GradientDrawable().apply {
                setColor(getColor(R.color.card)); cornerRadius = dp(12).toFloat()
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, dp(8))
            layoutParams = lp
        }
        attachFocusFx(r)
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        texts.addView(TextView(this).apply {
            this.text = title; setTextColor(Color.WHITE); textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        })
        if (sub.isNotEmpty()) texts.addView(TextView(this).apply {
            this.text = sub; setTextColor(getColor(R.color.muted)); textSize = 12f
            setPadding(0, dp(3), 0, 0)
        })
        r.addView(texts)
        if (tag.isNotEmpty()) r.addView(TextView(this).apply {
            this.text = tag; setTextColor(getColor(R.color.muted)); textSize = 12f
            setPadding(dp(10), 0, 0, 0)
        })
        if (onClick != null) r.setOnClickListener { onClick() }
        body.addView(r)
        return r
    }

    /** Big accent action row (start/stop, update lists…). */
    protected fun action(text: String, danger: Boolean = false, onClick: () -> Unit): TextView {
        val a = TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(15), dp(16), dp(15))
            isFocusable = true
            isClickable = true
            background = GradientDrawable().apply {
                setColor(if (danger) getColor(R.color.bad) else getColor(R.color.accent))
                cornerRadius = dp(12).toFloat()
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, dp(6), 0, dp(8))
            layoutParams = lp
        }
        a.setOnFocusChangeListener { v, has -> v.elevation = if (has) 8f else 0f; v.alpha = if (has) 1f else 0.85f }
        a.setOnClickListener { onClick() }
        body.addView(a)
        return a
    }

    protected fun note(text: String): TextView {
        val t = TextView(this).apply {
            this.text = text
            setTextColor(getColor(R.color.muted))
            textSize = 12f
            setPadding(0, dp(4), 0, dp(8))
        }
        body.addView(t)
        return t
    }

    /** Simple labeled toggle row backed by a value getter/setter. */
    protected fun toggle(label: String, sub: String = "", get: () -> Boolean, set: (Boolean) -> Unit): LinearLayout {
        lateinit var tv: TextView
        val r = row(label, sub, "") {
            set(!get())
            tv.text = if (get()) "ON" else "OFF"
            tv.setTextColor(getColor(if (get()) R.color.good else R.color.muted))
        }
        tv = TextView(this).apply {
            text = if (get()) "ON" else "OFF"
            setTextColor(getColor(if (get()) R.color.good else R.color.muted))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), 0, 0, 0)
        }
        r.addView(tv)
        return r
    }
}
