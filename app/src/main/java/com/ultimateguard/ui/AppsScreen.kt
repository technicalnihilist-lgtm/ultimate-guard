package com.ultimateguard.ui

import android.app.AlertDialog
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView
import com.ultimateguard.MainActivity
import com.ultimateguard.R
import com.ultimateguard.core.Admin
import com.ultimateguard.core.AppInventory
import java.util.concurrent.Executors

class AppsScreen(private val activity: MainActivity) : Screen {

    private val root: View = activity.layoutInflater.inflate(R.layout.screen_apps, null, false)
    private val list = root.findViewById<ListView>(R.id.appList)
    private val search = root.findViewById<EditText>(R.id.appSearch)
    private val systemSwitch = root.findViewById<Switch>(R.id.switchSystem)
    private val hint = root.findViewById<TextView>(R.id.appsHint)

    private val executor = Executors.newSingleThreadExecutor()
    private var all: List<AppInventory.AppInfo> = emptyList()
    private var shown: List<AppInventory.AppInfo> = emptyList()
    private var loading = false
    private val adapter = AppAdapter()

    init {
        list.adapter = adapter
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) = applyFilter()
        })
        systemSwitch.setOnCheckedChangeListener { _, _ -> load() }
        list.setOnItemClickListener { _, _, position, _ ->
            val info = shown.getOrNull(position) ?: return@setOnItemClickListener
            toggle(info)
        }
        list.setOnItemLongClickListener { _, _, position, _ ->
            shown.getOrNull(position)?.let { info ->
                AlertDialog.Builder(activity)
                    .setTitle(info.label)
                    .setMessage(info.packageName)
                    .setPositiveButton("App info") { _, _ -> activity.inventory.openAppInfo(info.packageName) }
                    .setNegativeButton("Close", null)
                    .show()
            }
            true
        }
    }

    override fun view(): View = root

    override fun refresh() {
        if (all.isEmpty() && !loading) load()
        updateHint()
    }

    private fun load() {
        loading = true
        hint.text = "Loading apps…"
        val includeSystem = systemSwitch.isChecked
        executor.execute {
            val apps = runCatching { activity.inventory.load(includeSystem) }.getOrDefault(emptyList())
            activity.runOnUiThread {
                all = apps
                loading = false
                applyFilter()
                updateHint()
            }
        }
    }

    private fun applyFilter() {
        val query = search.text?.toString()?.trim()?.lowercase().orEmpty()
        shown = if (query.isEmpty()) all else all.filter {
            it.label.lowercase().contains(query) || it.packageName.lowercase().contains(query)
        }
        adapter.notifyDataSetChanged()
    }

    private fun updateHint() {
        hint.text = when {
            loading -> "Loading apps…"
            !Admin.canManagePackages(activity) ->
                "${shown.size} apps · Device Owner not set, so blocks are saved but Android will not suspend apps yet"
            else -> "${shown.size} apps · ${activity.store.blockedPackages().size} blocked"
        }
    }

    private fun toggle(info: AppInventory.AppInfo) {
        val blocked = activity.store.isBlocked(info.packageName)
        if (!blocked) {
            activity.store.add(info.packageName)
            if (Admin.canManagePackages(activity)) {
                val suspended = Admin.isSuspended(activity, info.packageName)
                activity.toast(
                    if (suspended) "${info.label} blocked and suspended"
                    else "${info.label} blocked, but Android refused to suspend it"
                )
            } else {
                activity.toast("${info.label} saved as blocked — needs Device Owner to suspend")
            }
        } else {
            activity.store.remove(info.packageName)
            activity.toast("${info.label} unblocked")
        }
        adapter.notifyDataSetChanged()
        updateHint()
    }

    private inner class AppAdapter : BaseAdapter() {
        override fun getCount(): Int = shown.size
        override fun getItem(position: Int): Any = shown[position]
        override fun getItemId(position: Int): Long = shown[position].packageName.hashCode().toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: activity.layoutInflater.inflate(R.layout.item_app, parent, false)
            val info = shown[position]
            view.findViewById<TextView>(R.id.appName).text = info.label
            view.findViewById<TextView>(R.id.appPkg).text =
                if (info.isSystem) "${info.packageName} · system" else info.packageName
            val icon = view.findViewById<ImageView>(R.id.appIcon)
            if (info.icon != null) icon.setImageDrawable(info.icon) else icon.setImageResource(R.drawable.ic_shield)
            val toggleSwitch = view.findViewById<Switch>(R.id.appSwitch)
            toggleSwitch.isChecked = activity.store.isBlocked(info.packageName)
            return view
        }
    }
}
