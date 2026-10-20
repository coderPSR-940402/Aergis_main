package com.airgesture.control

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.lang.ref.WeakReference

/** Local launchable-app chooser; gesture scrolling selects and right swipe launches. */
class AppSearchActivity : Activity() {
    private data class App(val label: String, val packageName: String, val className: String)
    private var apps = emptyList<App>()
    private var shown = emptyList<App>()
    private var selected = 0
    private lateinit var list: ListView
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        val resolved = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        apps = resolved.map { App(it.loadLabel(packageManager).toString(), it.activityInfo.packageName, it.activityInfo.name) }
            .distinctBy { it.packageName to it.className }.sortedBy { it.label.lowercase() }
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        layout.addView(TextView(this).apply { setText(R.string.app_search_help) })
        val query = EditText(this).apply { setHint(R.string.action_app_search); setSingleLine(true) }
        layout.addView(query)
        list = ListView(this).apply { choiceMode = ListView.CHOICE_MODE_SINGLE }
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, mutableListOf<String>())
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ -> selected = position; launchSelected() }
        layout.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(layout)
        query.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = filter(s.toString())
            override fun afterTextChanged(s: Editable?) = Unit
        })
        filter("")
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    }
    private fun filter(query: String) {
        shown = apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
        selected = 0; adapter.clear(); adapter.addAll(shown.map { it.label }); adapter.notifyDataSetChanged()
        if (shown.isNotEmpty()) list.setItemChecked(0, true)
    }
    internal fun control(action: AirAction): Boolean {
        when (action) {
            AirAction.SCROLL_UP -> selected = (selected - 1).coerceAtLeast(0)
            AirAction.SCROLL_DOWN -> selected = (selected + 1).coerceAtMost((shown.size - 1).coerceAtLeast(0))
            AirAction.SWIPE_RIGHT -> { launchSelected(); return true }
            AirAction.SWIPE_LEFT -> { finish(); return true }
            else -> return false
        }
        if (shown.isNotEmpty()) { list.setItemChecked(selected, true); list.smoothScrollToPosition(selected) }
        AirRuntime.recordActionDispatchOutcome(ActionDispatchOutcome.COMPLETED)
        return true
    }
    private fun launchSelected() {
        val app = shown.getOrNull(selected) ?: return
        val launched = runCatching {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(app.packageName, app.className).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        AirRuntime.recordActionDispatchOutcome(if (launched) ActionDispatchOutcome.SUBMITTED else ActionDispatchOutcome.PLATFORM_REJECTED)
        if (launched) finish()
    }
    override fun onResume() { super.onResume(); active = WeakReference(this) }
    override fun onPause() { if (active.get() === this) active.clear(); super.onPause() }
    companion object { internal var active = WeakReference<AppSearchActivity>(null) }
}
