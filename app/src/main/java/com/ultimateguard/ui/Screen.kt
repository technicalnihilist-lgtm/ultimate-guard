package com.ultimateguard.ui

import android.content.Intent
import android.view.View

/** A tab's view plus the logic that keeps it up to date. */
interface Screen {
    fun view(): View
    fun refresh()
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {}
}
