package com.ultimateguard.ui

import android.app.AlertDialog
import android.content.Intent
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import com.ultimateguard.MainActivity
import com.ultimateguard.R
import com.ultimateguard.core.Admin
import com.ultimateguard.core.PinStore
import java.util.concurrent.Executors

class SettingsScreen(private val activity: MainActivity) : Screen {

    private val root: View = activity.layoutInflater.inflate(R.layout.screen_settings, null, false)
    private val pinState = root.findViewById<TextView>(R.id.pinState)
    private val btnPin = root.findViewById<Button>(R.id.btnPin)
    private val btnRemovePin = root.findViewById<Button>(R.id.btnRemovePin)
    private val upstreamValue = root.findViewById<TextView>(R.id.upstreamValue)
    private val switchIntercept = root.findViewById<Switch>(R.id.switchIntercept)
    private val switchAutostart = root.findViewById<Switch>(R.id.switchAutostart)
    private val switchUninstall = root.findViewById<Switch>(R.id.switchUninstall)
    private val uninstallHint = root.findViewById<TextView>(R.id.uninstallHint)
    private val executor = Executors.newSingleThreadExecutor()
    private var updatingSwitches = false

    init {
        btnPin.setOnClickListener {
            activity.promptNewPin { refresh() }
        }
        btnRemovePin.setOnClickListener {
            if (!PinStore.isSet(activity.prefs)) {
                activity.toast("No PIN is set")
                return@setOnClickListener
            }
            activity.askPin("Confirm PIN to remove it") {
                PinStore.clear(activity.prefs)
                activity.toast("PIN removed — the app is no longer locked")
                refresh()
            }
        }
        root.findViewById<Button>(R.id.btnUpstream).setOnClickListener { chooseUpstream() }

        switchIntercept.isChecked = activity.prefs.interceptHardcodedDns
        switchIntercept.setOnCheckedChangeListener { _, checked ->
            activity.prefs.interceptHardcodedDns = checked
            if (checked) {
                activity.toast("Restart protection for this to take effect")
            }
        }

        switchAutostart.isChecked = activity.prefs.autoStartOnBoot
        switchAutostart.setOnCheckedChangeListener { _, checked -> activity.prefs.autoStartOnBoot = checked }

        switchUninstall.isChecked = activity.prefs.uninstallProtection
        switchUninstall.setOnCheckedChangeListener { _, checked ->
            if (updatingSwitches) return@setOnCheckedChangeListener
            if (!Admin.isDeviceOwner(activity)) {
                updatingSwitches = true
                switchUninstall.isChecked = false
                updatingSwitches = false
                activity.showText(
                    "Device Owner required",
                    "Only a Device Owner app can stop itself from being uninstalled. " +
                        "See the Status tab for setup instructions."
                )
                return@setOnCheckedChangeListener
            }
            val ok = Admin.applySelfProtection(activity, checked)
            activity.prefs.uninstallProtection = checked && ok
            if (!ok) {
                switchUninstall.isChecked = false
                activity.toast("Android refused the request")
            }
        }

        root.findViewById<Button>(R.id.btnReapply).setOnClickListener {
            executor.execute {
                val failed = activity.store.reapply()
                activity.runOnUiThread {
                    activity.toast(
                        if (failed == 0) "Blocks re-applied" else "$failed app(s) could not be suspended"
                    )
                }
            }
        }

        root.findViewById<Button>(R.id.btnReloadLists).setOnClickListener {
            executor.execute {
                activity.repository.reload()
                activity.runOnUiThread {
                    activity.toast("Lists reloaded: ${activity.repository.totalCount} domains")
                    refresh()
                }
            }
        }

        root.findViewById<Button>(R.id.btnResetStats).setOnClickListener {
            activity.askPin("Confirm PIN to reset statistics") {
                activity.prefs.resetStats()
                activity.toast("Statistics reset")
            }
        }

        root.findViewById<Button>(R.id.btnClearLog).setOnClickListener {
            activity.askPin("Confirm PIN to clear the log") {
                activity.recent.clear()
                activity.toast("Activity log cleared")
            }
        }

        root.findViewById<TextView>(R.id.limitsText).text = LIMITS
        root.findViewById<TextView>(R.id.aboutText).text = about()
    }

    override fun view(): View = root

    override fun refresh() {
        pinState.text = if (PinStore.isSet(activity.prefs)) {
            "PIN is set. Opening this app, and every change to the block settings, requires it."
        } else {
            "No PIN set. Anyone who picks up this phone can open the app and turn protection off."
        }
        btnPin.text = if (PinStore.isSet(activity.prefs)) "Change PIN" else "Set PIN"
        btnRemovePin.visibility = if (PinStore.isSet(activity.prefs)) View.VISIBLE else View.GONE
        upstreamValue.text = "${activity.prefs.upstreamPrimary}  (fallback ${activity.prefs.upstreamSecondary})"

        val owner = Admin.isDeviceOwner(activity)
        switchUninstall.isEnabled = owner
        uninstallHint.text = if (owner) {
            "Stops Android from uninstalling or disabling this app."
        } else {
            "Requires Device Owner."
        }
        updatingSwitches = true
        switchUninstall.isChecked = activity.prefs.uninstallProtection && owner
        switchIntercept.isChecked = activity.prefs.interceptHardcodedDns
        switchAutostart.isChecked = activity.prefs.autoStartOnBoot
        updatingSwitches = false
    }

    private fun chooseUpstream() {
        val labels = arrayOf(
            "Cloudflare — 1.1.1.1",
            "Google — 8.8.8.8",
            "Quad9 — 9.9.9.9",
            "Custom"
        )
        val primaries = arrayOf("1.1.1.1", "8.8.8.8", "9.9.9.9", "")
        val secondaries = arrayOf("1.0.0.1", "8.8.4.4", "149.112.112.112", "")

        val current = primaries.indexOf(activity.prefs.upstreamPrimary).coerceAtLeast(0)
        AlertDialog.Builder(activity)
            .setTitle("Upstream resolver")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                dialog.dismiss()
                if (which == 3) {
                    promptCustomUpstream()
                } else {
                    activity.prefs.upstreamPrimary = primaries[which]
                    activity.prefs.upstreamSecondary = secondaries[which]
                    activity.toast("Resolver set to ${primaries[which]}")
                    refresh()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptCustomUpstream() {
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "IP address, e.g. 94.140.14.14"
            setPadding(40, 30, 40, 30)
            setText(activity.prefs.upstreamPrimary)
        }
        AlertDialog.Builder(activity)
            .setTitle("Custom resolver")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val value = input.text.toString().trim()
                if (!isIpAddress(value)) {
                    activity.toast("That is not a valid IP address")
                    return@setPositiveButton
                }
                activity.prefs.upstreamPrimary = value
                activity.prefs.upstreamSecondary = ""
                activity.toast("Resolver set to $value")
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun isIpAddress(value: String): Boolean = runCatching {
        val parts = value.split(".")
        if (parts.size == 4 && parts.all { it.toIntOrNull()?.let { n -> n in 0..255 } == true }) return true
        value.contains(":") && java.net.InetAddress.getByName(value) is java.net.Inet6Address
    }.getOrDefault(false)

    private fun about(): String {
        val version = runCatching {
            activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
        }.getOrDefault("1.0.0")
        return "Ultimate Guard $version\n" +
            "Offline DNS filter · no account, no telemetry, no network server.\n" +
            "Blocklists and settings never leave this device."
    }

    companion object {
        private const val LIMITS =
            "• This is a DNS filter. It only sees lookups that go through the system resolver, " +
                "plus the public resolvers it intercepts. An app that uses its own encrypted DNS " +
                "(DoH/DoT) to a server the app does not intercept can still resolve names.\n\n" +
                "• Ads served from the same domain as the page, or bundled inside an app, cannot be " +
                "blocked at the DNS level.\n\n" +
                "• Only one VPN can be active at a time. Another VPN app will take over the tunnel.\n\n" +
                "• The bundled lists are curated starter lists. No list blocks everything, and the " +
                "adult list is deliberately small — import a larger list you trust if you need one.\n\n" +
                "• Suspending apps needs Device Owner. Some system apps cannot be suspended, and " +
                "blocking critical packages can make the device hard to use.\n\n" +
                "• Nothing here survives a factory reset, and a person with recovery or ADB access " +
                "can always bypass it. This is a self-control and family-safety tool, not a " +
                "tamper-proof lockdown."
    }
}
