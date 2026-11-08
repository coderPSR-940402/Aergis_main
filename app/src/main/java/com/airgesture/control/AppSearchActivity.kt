package com.airgesture.control

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.airgesture.control.ui.AergisColors
import java.lang.ref.WeakReference
import java.text.Collator

/** Local launchable-app chooser; gesture scrolling selects and right swipe launches. */
class AppSearchActivity : ComponentActivity() {
    private data class App(val label: String, val packageName: String, val className: String)
    private var apps = emptyList<App>()
    private var shown = emptyList<App>()
    private var selected = 0
    private lateinit var list: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var searchField: EditText
    private lateinit var emptyState: TextView
    private var duplicateLabels = emptySet<String>()
    private var duplicatePackageLabels = emptySet<Pair<String, String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        @Suppress("DEPRECATION")
        val resolved = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val collator = Collator.getInstance()
        apps = resolved.map { App(it.loadLabel(packageManager).toString(), it.activityInfo.packageName, it.activityInfo.name) }
            .distinctBy { it.packageName to it.className }
            .sortedWith { first, second -> collator.compare(first.label, second.label) }
        duplicateLabels = apps.groupingBy { it.label }.eachCount().filterValues { it > 1 }.keys
        duplicatePackageLabels = apps.groupingBy { it.label to it.packageName }.eachCount().filterValues { it > 1 }.keys

        val gutter = (20 * resources.displayMetrics.density).toInt()
        val minTarget = (48 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AergisColors.Background.toArgb())
            isFocusableInTouchMode = true
        }
        ViewCompat.setOnApplyWindowInsetsListener(layout) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(gutter + bars.left, bars.top, gutter + bars.right, bars.bottom)
            insets
        }
        val heading = TextView(this).apply {
            setText(R.string.product_app_search_title)
            setTextColor(AergisColors.Text.toArgb())
            textSize = 34f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            ViewCompat.setAccessibilityHeading(this, true)
        }
        layout.addView(heading)
        layout.addView(TextView(this).apply {
            setText(R.string.app_search_help)
            setTextColor(AergisColors.Muted.toArgb())
            textSize = 14f
        })
        searchField = EditText(this).apply {
            setHint(R.string.product_app_search_query)
            setSingleLine(true)
            minHeight = minTarget
            imeOptions = EditorInfo.IME_ACTION_DONE
            setText(savedInstanceState?.getString("app_query").orEmpty())
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener false
                ViewCompat.getWindowInsetsController(this)?.hide(WindowInsetsCompat.Type.ime())
                true
            }
        }
        layout.addView(searchField)
        emptyState = TextView(this).apply {
            setText(R.string.product_app_search_empty)
            setTextColor(AergisColors.Purple.toArgb())
            setPadding(0, gutter, 0, gutter)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        layout.addView(emptyState)
        list = ListView(this).apply { choiceMode = ListView.CHOICE_MODE_SINGLE }
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, mutableListOf<String>())
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ -> selected = position; launchSelected() }
        layout.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        layout.addView(Button(this).apply {
            setText(R.string.product_app_search_close)
            minHeight = minTarget
            setOnClickListener { finish() }
        })
        setContentView(layout)
        layout.requestFocus()
        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = filter(s.toString())
            override fun afterTextChanged(s: Editable?) = Unit
        })
        filter(searchField.text.toString())
        val pkg = savedInstanceState?.getString("app_selected_package")
        val cls = savedInstanceState?.getString("app_selected_class")
        val restored = shown.indexOfFirst { it.packageName == pkg && it.className == cls }
        if (restored >= 0) {
            selected = restored
            list.setItemChecked(selected, true)
            list.setSelection(selected)
        }
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("app_query", searchField.text.toString())
        shown.getOrNull(selected)?.let {
            outState.putString("app_selected_package", it.packageName)
            outState.putString("app_selected_class", it.className)
        }
        super.onSaveInstanceState(outState)
    }

    private fun filter(query: String) {
        val term = query.trim()
        shown = apps.filter { it.label.contains(term, true) || it.packageName.contains(term, true) }
        selected = 0
        list.clearChoices()
        adapter.setNotifyOnChange(false)
        adapter.clear()
        adapter.addAll(shown.map {
            when {
                (it.label to it.packageName) in duplicatePackageLabels ->
                    getString(R.string.product_app_search_duplicate, it.label,
                        android.content.ComponentName(it.packageName, it.className).flattenToShortString())
                it.label in duplicateLabels -> getString(R.string.product_app_search_duplicate, it.label, it.packageName)
                else -> it.label
            }
        })
        adapter.notifyDataSetChanged()
        emptyState.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        if (shown.isNotEmpty()) {
            list.setItemChecked(0, true)
            list.setSelection(0)
        }
    }

    internal fun control(action: AirAction, record: (ActionDispatchOutcome) -> Unit = AirRuntime::recordActionDispatchOutcome): Boolean {
        when (action) {
            AirAction.SCROLL_UP, AirAction.PREVIOUS_ITEM -> selected = (selected - 1).coerceAtLeast(0)
            AirAction.SCROLL_DOWN, AirAction.NEXT_ITEM -> selected = (selected + 1).coerceAtMost((shown.size - 1).coerceAtLeast(0))
            AirAction.SWIPE_RIGHT, AirAction.SELECT_ITEM -> { launchSelected(record); return true }
            AirAction.SWIPE_LEFT -> { finish(); record(ActionDispatchOutcome.COMPLETED); return true }
            else -> return false
        }
        if (shown.isNotEmpty()) { list.setItemChecked(selected, true); list.smoothScrollToPosition(selected) }
        record(ActionDispatchOutcome.COMPLETED)
        return true
    }
    private fun launchSelected(record: (ActionDispatchOutcome) -> Unit = AirRuntime::recordActionDispatchOutcome) {
        val app = shown.getOrNull(selected) ?: run {
            record(ActionDispatchOutcome.PLATFORM_UNAVAILABLE)
            return
        }
        val outcome = try {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(app.packageName, app.className).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ActionDispatchOutcome.SUBMITTED
        } catch (_: android.content.ActivityNotFoundException) {
            ActionDispatchOutcome.PLATFORM_UNAVAILABLE
        } catch (_: SecurityException) {
            ActionDispatchOutcome.PLATFORM_REJECTED
        } catch (_: RuntimeException) {
            ActionDispatchOutcome.EXECUTION_FAILED
        }
        record(outcome)
        if (outcome == ActionDispatchOutcome.SUBMITTED) finish()
    }
    override fun onResume() { super.onResume(); active = WeakReference(this) }
    override fun onPause() { if (active.get() === this) active.clear(); super.onPause() }
    companion object { internal var active = WeakReference<AppSearchActivity>(null) }
}
