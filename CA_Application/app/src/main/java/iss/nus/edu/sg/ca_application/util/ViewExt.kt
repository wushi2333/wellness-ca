package iss.nus.edu.sg.ca_application.util

import android.view.View

/** Prevent rapid clicks — ignores taps within [intervalMs] of the last one. */
fun View.onClickDebounced(intervalMs: Long = 600L, action: (View) -> Unit) {
    var lastClick = 0L
    setOnClickListener { v ->
        val now = System.currentTimeMillis()
        if (now - lastClick >= intervalMs) {
            lastClick = now
            action(v)
        }
    }
}
