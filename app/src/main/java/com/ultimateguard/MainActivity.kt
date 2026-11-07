package com.ultimateguard

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.ultimateguard.core.AppInventory
import com.ultimateguard.core.BlockStore
import com.ultimateguard.core.BlocklistRepository
import com.ultimateguard.core.GuardState
import com.ultimateguard.core.PinStore
import com.ultimateguard.core.Prefs
import com.ultimateguard.core.RecentLog
import com.ultimateguard.ui.AppsScreen
import com.ultimateguard.ui.DomainsScreen
import com.ultimateguard.ui.HomeScreen
import com.ultimateguard.ui.Screen
import com.ultimateguard.ui.SettingsScreen

class MainActivity : Activity() {

    lateinit var prefs: Prefs
    lateinit var repository: BlocklistRepository
    lateinit var recent: RecentLog
    lateinit var store: BlockStore
    lateinit var inventory: AppInventory

    private lateinit var screenHost: FrameLayout
    private lateinit var overlayHost: FrameLayout
    private lateinit var topStatus: TextView
    private val screens = LinkedHashMap<Int, Screen>()
    private var currentTab = R.id.tabStatus
    private var unlocked = false
    private var pinAttempts = 0

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            refreshChrome()
            screens[currentTab]?.refresh()
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = Prefs(this)
        repository = BlocklistRepository(this)
        recent = RecentLog(this)
        store = BlockStore(this)
        inventory = AppInventory(this)

        screenHost = findViewById(R.id.screenHost)
        overlayHost = findViewById(R.id.overlayHost)
        topStatus = findViewById(R.id.topStatus)

        screens[R.id.tabStatus] = HomeScreen(this)
        screens[R.id.tabApps] = AppsScreen(this)
        screens[R.id.tabDomains] = DomainsScreen(this)
        screens[R.id.tabSettings] = SettingsScreen(this)

        findViewById<TextView>(R.id.tabStatus).setOnClickListener { selectTab(R.id.tabStatus) }
        findViewById<TextView>(R.id.tabApps).setOnClickListener { selectTab(R.id.tabApps) }
        findViewById<TextView>(R.id.tabDomains).setOnClickListener { selectTab(R.id.tabDomains) }
        findViewById<TextView>(R.id.tabSettings).setOnClickListener { selectTab(R.id.tabSettings) }

        selectTab(R.id.tabStatus)
        refreshChrome()

        Thread { runCatching { repository.reload() } }.start()
        requestNotificationPermission()
        gate()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    @Deprecated("Framework activity without AndroidX")
    override fun onBackPressed() {
        if (!unlocked) {
            finish()
            return
        }
        if (currentTab != R.id.tabStatus) selectTab(R.id.tabStatus) else finish()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = checkSelfPermission("android.permission.POST_NOTIFICATIONS") ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                runCatching { requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 77) }
            }
        }
    }

    // ---- PIN gate -------------------------------------------------------

    private fun gate() {
        overlayHost.removeAllViews()
        if (!PinStore.isSet(prefs)) {
            unlocked = true
            if (!prefs.onboardingDone) showFirstRun()
            return
        }
        unlocked = false
        showPinOverlay()
    }

    private fun showPinOverlay() {
        val view = layoutInflater.inflate(R.layout.screen_pin, overlayHost, false)
        val input = view.findViewById<EditText>(R.id.pinInput)
        val error = view.findViewById<TextView>(R.id.pinError)
        view.findViewById<Button>(R.id.pinConfirm).setOnClickListener {
            val pin = input.text.toString()
            if (pin.isEmpty()) {
                error.text = "Enter your PIN"
                return@setOnClickListener
            }
            if (PinStore.verify(prefs, pin)) {
                unlocked = true
                pinAttempts = 0
                overlayHost.removeAllViews()
                refreshChrome()
            } else {
                pinAttempts++
                error.text = "Wrong PIN ($pinAttempts attempt${if (pinAttempts == 1) "" else "s"})"
                input.setText("")
            }
        }
        view.findViewById<Button>(R.id.pinCancel).setOnClickListener { finish() }
        overlayHost.addView(view)
        input.requestFocus()
    }

    private fun showFirstRun() {
        prefs.onboardingDone = true
        AlertDialog.Builder(this)
            .setTitle("Welcome to Ultimate Guard")
            .setMessage(
                "This app filters DNS on this device: domains you block are answered locally " +
                    "instead of being looked up, so the app or site never loads.\n\n" +
                    "Set an admin PIN so the block settings cannot be changed by someone else. " +
                    "Keep the PIN somewhere safe — there is no way to recover it."
            )
            .setPositiveButton("Set PIN") { _, _ -> promptNewPin() }
            .setNegativeButton("Later") { _, _ -> }
            .show()
    }

    fun promptNewPin(after: (() -> Unit)? = null) {
        val first = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "4–12 digits"
            setPadding(40, 30, 40, 30)
        }
        AlertDialog.Builder(this)
            .setTitle("Choose a PIN")
            .setView(first)
            .setPositiveButton("Next") { _, _ ->
                val pin = first.text.toString()
                if (pin.length < 4) {
                    toast("Use at least 4 digits")
                    return@setPositiveButton
                }
                val second = EditText(this).apply {
                    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                    hint = "Repeat PIN"
                    setPadding(40, 30, 40, 30)
                }
                AlertDialog.Builder(this)
                    .setTitle("Repeat the PIN")
                    .setView(second)
                    .setPositiveButton("Save") { _, _ ->
                        if (second.text.toString() != pin) {
                            toast("PINs did not match")
                            return@setPositiveButton
                        }
                        PinStore.setPin(prefs, pin)
                        unlocked = true
                        toast("PIN saved")
                        after?.invoke()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun askPin(title: String, onOk: () -> Unit) {
        if (!PinStore.isSet(prefs)) {
            onOk()
            return
        }
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "PIN"
            setPadding(40, 30, 40, 30)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("Confirm") { _, _ ->
                if (PinStore.verify(prefs, input.text.toString())) onOk() else toast("Wrong PIN")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---- tabs -----------------------------------------------------------

    private fun selectTab(id: Int) {
        currentTab = id
        val screen = screens[id] ?: return
        screenHost.removeAllViews()
        val view: View = screen.view()
        if (view.parent != null) (view.parent as FrameLayout).removeView(view)
        screenHost.addView(view)
        for (tabId in screens.keys) {
            val label = findViewById<TextView>(tabId)
            val selected = tabId == id
            label.setTextColor(if (selected) getColor(R.color.accent) else getColor(R.color.muted))
            label.typeface = if (selected) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
        }
        screen.refresh()
    }

    fun refreshChrome() {
        val on = GuardState.active.get()
        topStatus.text = if (on) getString(R.string.status_on) else getString(R.string.status_off)
        topStatus.setTextColor(getColor(if (on) R.color.accent else R.color.danger))
        topStatus.setBackgroundResource(if (on) R.drawable.bg_pill_on else R.drawable.bg_pill_off)
    }

    // ---- service control ------------------------------------------------

    fun startProtection() {
        val consent = VpnService.prepare(this)
        if (consent != null) {
            startActivityForResult(consent, REQUEST_VPN)
            return
        }
        val intent = Intent(this, FilteringVpnService::class.java)
            .setAction(FilteringVpnService.ACTION_START)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
            else startService(intent)
        }.onFailure { toast("Could not start protection: ${it.message}") }
        handler.postDelayed({ refreshChrome(); screens[currentTab]?.refresh() }, 600)
    }

    fun stopProtection() {
        runCatching {
            startService(Intent(this, FilteringVpnService::class.java).setAction(FilteringVpnService.ACTION_STOP))
        }
        GuardState.active.set(false)
        handler.postDelayed({ refreshChrome(); screens[currentTab]?.refresh() }, 400)
    }

    @Deprecated("Framework activity without AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_VPN) {
            if (resultCode == RESULT_OK) startProtection() else toast("VPN permission was not granted")
        }
        screens.values.forEach { it.onActivityResult(requestCode, resultCode, data) }
    }

    // ---- helpers --------------------------------------------------------

    fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    fun showText(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Close", null)
            .show()
    }

    /** Simple dialog with a vertical list of rows and an optional action per row. */
    fun showList(
        title: String,
        rows: List<Pair<String, String>>,
        onRow: ((Int) -> Unit)? = null,
        emptyText: String = "Nothing here yet."
    ) {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroller = android.widget.ScrollView(this)
        container.setPadding(40, 20, 40, 20)
        if (rows.isEmpty()) {
            container.addView(TextView(this).apply {
                text = emptyText
                setTextColor(getColor(R.color.muted))
                textSize = 14f
                setPadding(0, 20, 0, 20)
            })
        }
        rows.forEachIndexed { index, row ->
            val item = layoutInflater.inflate(R.layout.item_row, container, false)
            item.findViewById<TextView>(R.id.rowTitle).text = row.first
            val sub = item.findViewById<TextView>(R.id.rowSub)
            if (row.second.isEmpty()) sub.visibility = View.GONE else sub.text = row.second
            if (onRow != null) item.setOnClickListener { onRow(index) }
            container.addView(item)
        }
        scroller.addView(container)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(scroller)
            .setPositiveButton("Close", null)
            .show()
    }

    companion object {
        private const val REQUEST_VPN = 1001
    }
}
