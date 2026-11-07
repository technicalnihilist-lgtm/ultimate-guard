package com.ultimateguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ultimateguard.core.BlockStore

/**
 * Re-applies a block when a blocked app is reinstalled or updated — Android
 * clears suspension state on package replace.
 */
class PackageChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val packages = intent.data?.schemeSpecificPart ?: return
        if (intent.action != Intent.ACTION_PACKAGE_ADDED && intent.action != Intent.ACTION_PACKAGE_REPLACED) return
        val store = BlockStore(context)
        if (store.isBlocked(packages)) store.reapply()
    }
}
