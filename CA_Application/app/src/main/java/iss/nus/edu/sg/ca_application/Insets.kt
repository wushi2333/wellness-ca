// Author: Xia Zihang
package iss.nus.edu.sg.ca_application

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Add top padding equal to status bar height so the view sits below the notch/status bar. */
fun View.applyTopInset() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
        v.setPadding(v.paddingLeft, statusBarHeight, v.paddingRight, v.paddingBottom)
        insets
    }
}
