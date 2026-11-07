package com.ultimateguard.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView
import com.ultimateguard.MainActivity
import com.ultimateguard.R
import com.ultimateguard.core.DomainMatcher
import java.util.concurrent.Executors

class DomainsScreen(private val activity: MainActivity) : Screen {

    private val root: View = activity.layoutInflater.inflate(R.layout.screen_domains, null, false)
    private val switchAds = root.findViewById<Switch>(R.id.switchAds)
    private val switchTrackers = root.findViewById<Switch>(R.id.switchTrackers)
    private val switchAdult = root.findViewById<Switch>(R.id.switchAdult)
    private val countAds = root.findViewById<TextView>(R.id.countAds)
    private val countTrackers = root.findViewById<TextView>(R.id.countTrackers)
    private val countAdult = root.findViewById<TextView>(R.id.countAdult)
    private val domainsTotal = root.findViewById<TextView>(R.id.domainsTotal)
    private val customCount = root.findViewById<TextView>(R.id.customCount)
    private val allowCount = root.findViewById<TextView>(R.id.allowCount)
    private val testInput = root.findViewById<EditText>(R.id.testInput)
    private val testResult = root.findViewById<TextView>(R.id.testResult)
    private val executor = Executors.newSingleThreadExecutor()
    private var reloading = false

    init {
        switchAds.isChecked = activity.prefs.listAds
        switchTrackers.isChecked = activity.prefs.listTrackers
        switchAdult.isChecked = activity.prefs.listAdult

        switchAds.setOnCheckedChangeListener { _, checked -> activity.prefs.listAds = checked; reload() }
        switchTrackers.setOnCheckedChangeListener { _, checked -> activity.prefs.listTrackers = checked; reload() }
        switchAdult.setOnCheckedChangeListener { _, checked -> activity.prefs.listAdult = checked; reload() }

        root.findViewById<Button>(R.id.btnManageCustom).setOnClickListener { manageCustom() }
        root.findViewById<Button>(R.id.btnManageAllow).setOnClickListener { manageAllow() }
        root.findViewById<Button>(R.id.btnImport).setOnClickListener { pickFile() }
        root.findViewById<Button>(R.id.btnTest).setOnClickListener { test() }
    }

    override fun view(): View = root

    override fun refresh() {
        countAds.text = "${activity.repository.adsCount} domains"
        countTrackers.text = "${activity.repository.trackersCount} domains"
        countAdult.text = "${activity.repository.adultCount} domains"
        domainsTotal.text = "${activity.repository.totalCount} domains active on this device"
        customCount.text = "${activity.repository.customCount} domains"
        allowCount.text = "${activity.repository.allowCount} domains"
    }

    private fun reload() {
        if (reloading) return
        reloading = true
        executor.execute {
            runCatching { activity.repository.reload() }
            activity.runOnUiThread {
                reloading = false
                refresh()
            }
        }
    }

    private fun test() {
        val raw = testInput.text?.toString()?.trim().orEmpty()
        val domain = DomainMatcher.normalize(raw)
        if (domain == null) {
            testResult.text = "That does not look like a domain name. Try something like ads.example.com"
            testResult.setTextColor(activity.getColor(R.color.muted))
            return
        }
        if (activity.repository.isBlocked(domain)) {
            testResult.text = "$domain is BLOCKED — lookups are answered locally and the site or app will not load."
            testResult.setTextColor(activity.getColor(R.color.danger))
        } else {
            testResult.text = "$domain is ALLOWED — lookups go to ${activity.prefs.upstreamPrimary}."
            testResult.setTextColor(activity.getColor(R.color.accent))
        }
    }

    private fun pickFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "text/*", "application/octet-stream"))
        }
        runCatching { activity.startActivityForResult(intent, REQ_IMPORT) }
            .onFailure { activity.toast("No file picker available") }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQ_IMPORT || resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        activity.toast("Importing…")
        executor.execute {
            val added = runCatching { activity.repository.importFrom(uri) }.getOrDefault(-1)
            activity.runOnUiThread {
                if (added < 0) {
                    activity.toast("Could not read that file")
                } else {
                    activity.toast("Imported $added new domains")
                    refresh()
                }
            }
        }
    }

    private fun manageCustom() {
        manageList(
            title = "Your blocked domains",
            domains = { activity.repository.customDomains() },
            onAdd = { raw ->
                val ok = activity.repository.addCustom(raw)
                if (!ok) activity.toast("Not a valid domain, or already in the list")
                ok
            },
            onRemove = { domain -> activity.repository.removeCustom(domain) }
        )
    }

    private fun manageAllow() {
        manageList(
            title = "Never block",
            domains = { activity.repository.allowDomains() },
            onAdd = { raw ->
                val ok = activity.repository.addAllowed(raw)
                if (!ok) activity.toast("Not a valid domain, or already in the list")
                ok
            },
            onRemove = { domain -> activity.repository.removeAllowed(domain) }
        )
    }

    private fun manageList(
        title: String,
        domains: () -> List<String>,
        onAdd: (String) -> Boolean,
        onRemove: (String) -> Unit
    ) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_list, null, false)
        val input = view.findViewById<EditText>(R.id.dialogInput)
        val listView = view.findViewById<ListView>(R.id.dialogList)
        val empty = view.findViewById<TextView>(R.id.dialogEmpty)
        var rows = domains()

        val adapter = SimpleAdapter(rows)
        listView.adapter = adapter
        empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE

        val dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(view)
            .setPositiveButton("Add", null)
            .setNegativeButton("Close", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val raw = input.text?.toString()?.trim().orEmpty()
                if (raw.isEmpty()) {
                    activity.toast("Type a domain first")
                    return@setOnClickListener
                }
                if (onAdd(raw)) {
                    input.setText("")
                    rows = domains()
                    adapter.replace(rows)
                    empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
                    refresh()
                }
            }
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val domain = rows.getOrNull(position) ?: return@setOnItemClickListener
            AlertDialog.Builder(activity)
                .setTitle(domain)
                .setMessage("Remove this entry?")
                .setPositiveButton("Remove") { _, _ ->
                    onRemove(domain)
                    rows = domains()
                    adapter.replace(rows)
                    empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
                    refresh()
                }
                .setNegativeButton("Keep", null)
                .show()
        }

        dialog.show()
    }

    private inner class SimpleAdapter(private var items: List<String>) : android.widget.BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup?): View {
            val view = convertView ?: activity.layoutInflater.inflate(R.layout.item_row, parent, false)
            view.findViewById<TextView>(R.id.rowTitle).text = items[position]
            view.findViewById<TextView>(R.id.rowSub).visibility = View.GONE
            return view
        }

        fun replace(newItems: List<String>) {
            items = newItems
            notifyDataSetChanged()
        }
    }

    companion object { private const val REQ_IMPORT = 2001 }
}
