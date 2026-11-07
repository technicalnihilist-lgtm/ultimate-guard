package com.ultimateguard.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.ultimateguard.MainActivity
import com.ultimateguard.R
import com.ultimateguard.core.Admin
import com.ultimateguard.core.GuardState

class HomeScreen(private val activity: MainActivity) : Screen {

    private val root: View = activity.layoutInflater.inflate(R.layout.screen_home, null, false)
    private val title = root.findViewById<TextView>(R.id.homeStatusTitle)
    private val subtitle = root.findViewById<TextView>(R.id.homeStatusSub)
    private val toggle = root.findViewById<Button>(R.id.btnToggle)
    private val statToday = root.findViewById<TextView>(R.id.statToday)
    private val statTotal = root.findViewById<TextView>(R.id.statTotal)
    private val statDomains = root.findViewById<TextView>(R.id.statDomains)
    private val recentContainer = root.findViewById<LinearLayout>(R.id.recentContainer)
    private val ownerTitle = root.findViewById<TextView>(R.id.ownerTitle)
    private val ownerSub = root.findViewById<TextView>(R.id.ownerSub)
    private val ownerButton = root.findViewById<Button>(R.id.btnOwner)

    init {
        toggle.setOnClickListener {
            if (GuardState.active.get()) confirmStop() else activity.startProtection()
        }
        root.findViewById<Button>(R.id.btnAllRecent).setOnClickListener { showLog() }
        root.findViewById<Button>(R.id.btnVpnSettings).setOnClickListener {
            runCatching { activity.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }
                .onFailure { activity.showText("VPN settings", "Open Settings › Network › VPN and enable always-on for Ultimate Guard.") }
        }
        ownerButton.setOnClickListener { showDeviceOwnerHelp() }
    }

    override fun view(): View = root

    override fun refresh() {
        val on = GuardState.active.get()
        title.text = if (on) activity.getString(R.string.status_on) else activity.getString(R.string.status_off)
        title.setTextColor(activity.getColor(if (on) R.color.accent else R.color.danger))
        subtitle.text = if (on) {
            val upstream = GuardState.upstreamInUse.ifBlank { activity.prefs.upstreamPrimary }
            "Filtering every DNS lookup on this device. Blocked names are answered locally; everything else goes to $upstream."
        } else {
            "DNS filtering is off. Nothing is being blocked right now."
        }
        toggle.text = if (on) "Turn off protection" else "Turn on protection"

        statToday.text = activity.prefs.blockedToday.toString()
        statTotal.text = activity.prefs.totalBlocked.toString()
        statDomains.text = (if (GuardState.domainsLoaded > 0) GuardState.domainsLoaded else activity.repository.totalCount).toString()

        renderRecent()
        renderOwner()
    }

    private fun renderRecent() {
        recentContainer.removeAllViews()
        val entries = activity.recent.snapshot(4)
        if (entries.isEmpty()) {
            recentContainer.addView(TextView(activity).apply {
                text = if (GuardState.active.get()) "No lookups yet. Browse for a while and they will show up here."
                else "Turn on protection to start collecting activity."
                setTextColor(activity.getColor(R.color.muted))
                textSize = 13f
            })
            return
        }
        for (entry in entries) {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
            }
            val domain = TextView(activity).apply {
                text = entry.domain
                setTextColor(activity.getColor(if (entry.blocked) R.color.danger else R.color.text))
                textSize = 13f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val state = TextView(activity).apply {
                text = if (entry.blocked) "blocked" else "allowed"
                setTextColor(activity.getColor(if (entry.blocked) R.color.danger else R.color.muted))
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
            }
            row.addView(domain)
            row.addView(state)
            recentContainer.addView(row)
        }
    }

    private fun renderOwner() {
        when {
            Admin.isDeviceOwner(activity) -> {
                ownerTitle.text = "App blocking: active (Device Owner)"
                ownerSub.text = "Blocked apps are suspended by Android and re-applied after every reboot."
                ownerButton.text = "Device Owner settings"
                ownerButton.setOnClickListener {
                    activity.showText(
                        "Device Owner",
                        "This device is managed by Ultimate Guard as Device Owner.\n\n" +
                            "Blocked apps are suspended through the DevicePolicyManager. " +
                            "You can remove Device Owner status with:\n\n" +
                            "adb shell dpm remove-active-admin com.ultimateguard/.GuardAdminReceiver"
                    )
                }
            }
            Admin.isProfileOwner(activity) -> {
                ownerTitle.text = "App blocking: active (Profile Owner)"
                ownerSub.text = "Apps can be suspended inside this managed profile."
            }
            else -> {
                ownerTitle.text = "App blocking: not available"
                ownerSub.text = "Android only lets an app suspend other apps when it is Device Owner. " +
                    "Domain filtering works without it; app blocks are stored and applied as soon as ownership is granted."
                ownerButton.text = "How to set up Device Owner"
                ownerButton.setOnClickListener { showDeviceOwnerHelp() }
            }
        }
    }

    private fun showDeviceOwnerHelp() {
        AlertDialog.Builder(activity)
            .setTitle("Setting up Device Owner")
            .setMessage(
                "Android only allows Device Owner to be granted on a device with no accounts, " +
                    "during setup or on a factory-reset test device.\n\n" +
                    "1. Factory reset the phone (use a spare device).\n" +
                    "2. At the welcome screen, enable USB debugging and connect it to a computer.\n" +
                    "3. Run:\n\n" +
                    "adb shell dpm set-device-owner com.ultimateguard/.GuardAdminReceiver\n\n" +
                    "4. Finish setup and open this app.\n\n" +
                    "Do not do this on a personal phone with data you need — removing Device Owner requires a factory reset."
            )
            .setPositiveButton("Got it", null)
            .show()
    }

    private fun confirmStop() {
        AlertDialog.Builder(activity)
            .setTitle("Turn off protection?")
            .setMessage("DNS filtering will stop and blocked domains will load normally until you turn it back on.")
            .setPositiveButton("Turn off") { _, _ -> activity.stopProtection() }
            .setNegativeButton("Keep on", null)
            .show()
    }

    private fun showLog() {
        val entries = activity.recent.snapshot(200)
        val rows = entries.map {
            val time = android.text.format.DateFormat.format("HH:mm:ss", it.time).toString()
            Pair(it.domain, "$time · ${if (it.blocked) "blocked" else "allowed"}")
        }
        activity.showList("Activity log", rows, emptyText = "No DNS activity recorded yet.")
    }
}
