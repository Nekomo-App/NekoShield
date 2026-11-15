package net.nekomo.nekoshield.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** Tiny uniform row adapter — title + optional subtitle + optional state tag. */
data class Row(
    val title: String,
    val sub: String = "",
    val tag: String = "",
    val tagColor: Int = 0,
    val onClick: (() -> Unit)? = null,
    val onLong: (() -> Unit)? = null,
)

class RowAdapter(private val items: MutableList<Row> = mutableListOf()) :
    RecyclerView.Adapter<RowAdapter.VH>() {

    class VH(val root: LinearLayout, val t: TextView, val s: TextView, val tag: TextView) :
        RecyclerView.ViewHolder(root)

    fun set(new: List<Row>) {
        items.clear(); items += new; notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(ctx, 18), dp(ctx, 14), dp(ctx, 18), dp(ctx, 14))
            isFocusable = true
            isClickable = true
            background = focusBg(ctx)
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val texts = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val t = TextView(ctx).apply {
            setTextColor(ctx.getColor(net.nekomo.nekoshield.R.color.text)); textSize = 15f; typeface = Typeface.DEFAULT_BOLD
        }
        val s = TextView(ctx).apply {
            setTextColor(ctx.getColor(net.nekomo.nekoshield.R.color.muted)); textSize = 12f; setPadding(0, dp(ctx, 3), 0, 0)
        }
        texts.addView(t); texts.addView(s)
        val tag = TextView(ctx).apply {
            textSize = 11f; typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(ctx, 10), dp(ctx, 4), dp(ctx, 10), dp(ctx, 4))
            background = GradientDrawable().apply {
                setColor(ctx.getColor(net.nekomo.nekoshield.R.color.accent_container))
                cornerRadius = dp(ctx, 12).toFloat()
            }
        }
        root.addView(texts); root.addView(tag)
        return VH(root, t, s, tag)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val r = items[position]
        h.t.text = r.title
        h.s.text = r.sub
        h.s.visibility = if (r.sub.isEmpty()) View.GONE else View.VISIBLE
        if (r.tag.isEmpty()) {
            h.tag.visibility = View.GONE
        } else {
            h.tag.visibility = View.VISIBLE
            h.tag.text = r.tag
            h.tag.setTextColor(r.tagColor.takeIf { it != 0 }
                ?: h.root.context.getColor(net.nekomo.nekoshield.R.color.accent_soft))
        }
        h.root.setOnClickListener { r.onClick?.invoke() }
        h.root.setOnLongClickListener { r.onLong?.invoke(); r.onLong != null }
    }

    override fun getItemCount() = items.size

    companion object {
        fun dp(ctx: Context, v: Int): Int =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()

        /** Rounded row bg that lights up on D-pad/touch focus. */
        fun focusBg(ctx: Context): GradientDrawable {
            return GradientDrawable().apply {
                setColor(ctx.getColor(net.nekomo.nekoshield.R.color.card))
                cornerRadius = dp(ctx, 16).toFloat()
                setStroke(dp(ctx, 1), ctx.getColor(net.nekomo.nekoshield.R.color.border))
            }
        }
    }
}

/** Applies the focused/unfocused visual via a state listener — a single
 * background drawable can't do it for rounded shapes, so we swap tints. */
fun attachFocusFx(v: View) {
    val bg = v.background
    v.setOnFocusChangeListener { view, has ->
        if (bg is GradientDrawable) {
            val ctx = view.context
            if (has) {
                bg.setColor(ctx.getColor(net.nekomo.nekoshield.R.color.card_focus))
                bg.setStroke(3, ctx.getColor(net.nekomo.nekoshield.R.color.accent_soft))
                view.elevation = 6f
            } else {
                bg.setColor(ctx.getColor(net.nekomo.nekoshield.R.color.card))
                bg.setStroke(2, ctx.getColor(net.nekomo.nekoshield.R.color.border))
                view.elevation = 0f
            }
        }
    }
}
