package com.ultimateguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ultimateguard.core.BlockStore
import com.ultimateguard.core.Prefs

/**
 * Re-applies stored app blocks and restarts the filter after a reboot.
 * (Always-on VPN in Android settings is still the most reliable way to keep
 * filtering running; this receiver covers the normal reboot case.)
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        runCatching { BlockStore(context).reapply() }

        val prefs = Prefs(context)
        if (!prefs.autoStartOnBoot) return
        if (VpnServiceHolder.prepare(context) != null) return

        val service = Intent(context, FilteringVpnService::class.java)
            .setAction(FilteringVpnService.ACTION_START)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(service)
            } else {
                context.startService(service)
            }
        }
    }
}

/** Small helper so receivers can check VPN consent without duplicating code. */
object VpnServiceHolder {
    fun prepare(context: Context) = android.net.VpnService.prepare(context)
}
