package com.ultimateguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Reapplies package suspensions after boot when permitted by the device's
 * managed-device policy and Android background-start rules.
 */
class BootReapplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            runCatching { PermanentBlockStore(context).reapplyPackageBlocks() }
        }
    }
}
