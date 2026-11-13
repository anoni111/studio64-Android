package com.studio64.android

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout

/**
 * Barra de teclas en pantalla (para usar el editor sin teclado fisico).
 * Cada boton envia keydown al pulsar y keyup al soltar. Ctrl y Shift son "pegajosos".
 */
class KeyBar(
    private val ctx: Context,
    private val send: (type: String, key: Key, ctrl: Boolean, shift: Boolean) -> Unit,
) {
    data class Key(val label: String, val key: String, val code: String, val kc: Int)

    private var ctrl = false
    private var shift = false
    val view: HorizontalScrollView

    private val keys = listOf(
        Key("Tab", "Tab", "Tab", 9), Key("Esc", "Escape", "Escape", 27),
        Key("W", "w", "KeyW", 87), Key("A", "a", "KeyA", 65), Key("S", "s", "KeyS", 83), Key("D", "d", "KeyD", 68),
        Key("Espacio", " ", "Space", 32), Key("Enter", "Enter", "Enter", 13),
        Key("↑", "ArrowUp", "ArrowUp", 38), Key("↓", "ArrowDown", "ArrowDown", 40),
        Key("←", "ArrowLeft", "ArrowLeft", 37), Key("→", "ArrowRight", "ArrowRight", 39),
        Key("Supr", "Delete", "Delete", 46),
        Key("Z", "z", "KeyZ", 90), Key("Y", "y", "KeyY", 89), Key("Q", "q", "KeyQ", 81), Key("E", "e", "KeyE", 69),
        Key("F", "f", "KeyF", 70),
        Key("1", "1", "Digit1", 49), Key("2", "2", "Digit2", 50), Key("3", "3", "Digit3", 51),
        Key("4", "4", "Digit4", 52), Key("5", "5", "Digit5", 53),
    )

    private val shiftKey = Key("Shift", "Shift", "ShiftLeft", 16)
    private val ctrlKey = Key("Ctrl", "Control", "ControlLeft", 17)

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    private fun paint(b: Button, on: Boolean) {
        b.background = GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
            setColor(if (on) 0xEE3B82F6.toInt() else 0xCC11161B.toInt())
            setStroke(dp(1), 0x66FFFFFF)
        }
    }

    private fun baseButton(label: String): Button = Button(ctx).apply {
        text = label
        textSize = 13f
        isAllCaps = false
        setTextColor(Color.WHITE)
        minWidth = dp(44)
        minimumWidth = dp(44)
        minHeight = dp(40)
        minimumHeight = dp(40)
        setPadding(dp(10), 0, dp(10), 0)
        paint(this, false)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)).apply {
            setMargins(dp(3), dp(3), dp(3), dp(3))
        }
    }

    init {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(2), dp(6), dp(2))
        }

        // modificadores pegajosos
        val shiftBtn = baseButton("Shift")
        shiftBtn.setOnClickListener {
            shift = !shift
            paint(shiftBtn, shift)
            send(if (shift) "keydown" else "keyup", shiftKey, ctrl, shift)
        }
        val ctrlBtn = baseButton("Ctrl")
        ctrlBtn.setOnClickListener {
            ctrl = !ctrl
            paint(ctrlBtn, ctrl)
            send(if (ctrl) "keydown" else "keyup", ctrlKey, ctrl, shift)
        }
        row.addView(shiftBtn)
        row.addView(ctrlBtn)

        for (k in keys) {
            val b = baseButton(k.label)
            @Suppress("ClickableViewAccessibility")
            b.setOnTouchListener { v, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        paint(b, true)
                        send("keydown", k, ctrl, shift)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        paint(b, false)
                        send("keyup", k, ctrl, shift)
                    }
                }
                true
            }
            row.addView(b)
        }

        view = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(0x88000000.toInt())
            addView(row)
            visibility = View.GONE
        }
    }
}
