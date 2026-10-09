package rocks.pkb.plektra

import android.app.Activity
import android.app.AlertDialog
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.PopupMenu
import androidx.core.widget.doAfterTextChanged
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.ext.tasklist.TaskListSpan
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin

open class NotesActivity : Activity() {
    protected lateinit var editor: EditText
    protected lateinit var heading: EditText
    protected lateinit var root: LinearLayout
    protected open val notesPreferencesName: String = "notes"
    private val notePrefs by lazy { getSharedPreferences(notesPreferencesName, MODE_PRIVATE) }
    private val notes by lazy { NotesRepository(notePrefs) }
    private lateinit var selectedNote: NotesRepository.Note
    private var loadingNote = false
    private val undo = ArrayDeque<String>()
    private val redo = ArrayDeque<String>()
    private var lastBody = ""
    private var restoringHistory = false
    private var continuingList = false
    private lateinit var preview: TextView
    private lateinit var previewScroll: ScrollView
    private lateinit var formatBar: HorizontalScrollView
    private lateinit var panes: LinearLayout
    private lateinit var paneDivider: View
    private val modeTabs = mutableListOf<TextView>()
    private var viewMode = ViewMode.MARKDOWN
    private var inkColor = Color.BLACK
    private var accentColor = Color.rgb(0, 119, 122)
    private var dividerColor = Color.LTGRAY
    private enum class ViewMode { MARKDOWN, BOTH, PREVIEW }
    private var restoreHeadingFocus = false
    private val markwon by lazy {
        Markwon.builder(this).usePlugin(TablePlugin.create(this))
            .usePlugin(TaskListPlugin.create(inkColor, dividerColor, Color.WHITE))
            .usePlugin(StrikethroughPlugin.create()).build()
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        selectedNote = notes.active()
        val dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val paper = if (dark) Color.rgb(28, 29, 31) else Color.rgb(250, 250, 248)
        val ink = if (dark) Color.rgb(237, 237, 235) else Color.rgb(30, 34, 36)
        inkColor = ink
        accentColor = if (dark) Color.rgb(101, 211, 208) else Color.rgb(0, 119, 122)
        dividerColor = if (dark) Color.rgb(65, 66, 68) else Color.rgb(218, 219, 216)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(paper)
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            setPadding(dp(24), dp(12), dp(24), dp(8))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(dp(24) + bars.left, dp(12) + bars.top, dp(24) + bars.right, dp(8) + maxOf(bars.bottom, ime.bottom))
            insets
        }
        val navigation = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        navigation.addView(iconAction(R.drawable.ic_notes_back, R.string.settings_back_content_description) { finish() })
        heading = EditText(this).apply {
            id = R.id.notes_heading
            textSize = 22f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(ink)
            setHintTextColor(if (dark) Color.LTGRAY else Color.GRAY)
            hint = getString(R.string.notes_untitled)
            background = null
            setSingleLine(true)
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_NEXT
            setText(selectedNote.title)
            setPadding(dp(8), dp(8), 0, dp(8))
        }
        navigation.addView(heading, LinearLayout.LayoutParams(0, dp(56), 1f))
        if (resources.configuration.screenWidthDp >= 600) navigation.addView(TextView(this).apply {
            text = "✓ ${getString(R.string.notes_saved)}"
            setTextColor(if (dark) Color.LTGRAY else Color.GRAY)
            textSize = 12f
            setPadding(dp(12), 0, dp(12), 0)
        })
        navigation.addView(iconAction(R.drawable.ic_notes_add, R.string.notes_new) { switchNote(notes.create(), focusHeading = true) })
        navigation.addView(iconAction(R.drawable.ic_notes_more, R.string.notes_title) { showNotes() })
        root.addView(navigation)
        val tabs = LinearLayout(this)
        listOf(R.string.notes_markdown, R.string.notes_both, R.string.notes_preview).forEachIndexed { index, label ->
            val tab = action(getString(label), getString(label), ink) { setViewMode(ViewMode.entries[index], showInput = true) }
            val icon = listOf(R.drawable.ic_notes_document, R.drawable.ic_notes_split, R.drawable.ic_notes_preview)[index]
            tab.text = android.text.SpannableString("  ${getString(label)}").apply {
                tintedIcon(icon)?.let { setSpan(android.text.style.ImageSpan(it, android.text.style.ImageSpan.ALIGN_CENTER), 0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
            }
            if (index == 2) tab.id = R.id.notes_preview_toggle
            if (index == 1) tab.id = R.id.notes_both_toggle
            modeTabs += tab
            tabs.addView(tab, LinearLayout.LayoutParams(0, dp(44), 1f))
        }
        root.addView(tabs)
        root.addView(View(this).apply { setBackgroundColor(dividerColor) }, LinearLayout.LayoutParams(-1, dp(1)))
        editor = object : EditText(this) {
            override fun isSuggestionsEnabled() = false
            private val gutterPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = if (dark) Color.LTGRAY else Color.GRAY
                textSize = dp(11).toFloat()
                textAlign = android.graphics.Paint.Align.RIGHT
            }
            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)
                val textLayout = layout ?: return
                var number = 1
                for (line in 0 until textLayout.lineCount) {
                    val offset = textLayout.getLineStart(line)
                    if (offset == 0 || text.getOrNull(offset - 1) == '\n') {
                        canvas.drawText(number++.toString(), dp(28).toFloat(),
                            (textLayout.getLineBaseline(line) + totalPaddingTop).toFloat(), gutterPaint)
                    }
                }
            }
        }.apply {
            id = R.id.notes_body
            textSize = 16f
            typeface = Typeface.MONOSPACE
            setTextColor(ink)
            setHintTextColor(if (dark) Color.LTGRAY else Color.GRAY)
            hint = getString(R.string.notes_body_hint)
            background = null
            gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText(selectedNote.body)
            setPadding(dp(44), dp(16), dp(16), dp(16))
            setLineSpacing(dp(4).toFloat(), 1f)
        }

        preview = TextView(this).apply {
            id = R.id.notes_preview_body
            textSize = 17f
            setTextColor(ink)
            setTextIsSelectable(true)
            setPadding(dp(20), dp(16), dp(8), dp(16))
            setOnTouchListener { _, event ->
                val layout = layout
                val source = text as? android.text.Spanned
                if (layout == null || source == null) false else {
                    val line = layout.getLineForVertical((event.y - totalPaddingTop).toInt())
                    val offset = layout.getLineStart(line)
                    val span = source.getSpans(offset, minOf(offset + 1, source.length), TaskListSpan::class.java).firstOrNull()
                    if (span == null || event.x > totalPaddingLeft + span.getLeadingMargin(true) + dp(8)) false else {
                        if (event.actionMasked == MotionEvent.ACTION_UP) togglePreviewCheckbox(span)
                        true
                    }
                }
            }
        }
        previewScroll = ScrollView(this).apply { addView(preview); visibility = View.GONE }

        val formats = LinearLayout(this)
        fun format(symbol: String, label: Int, before: String, after: String = "") {
            formats.addView(action(symbol, getString(label), ink) { insertMarkup(before, after) })
        }
        formats.addView(iconAction(R.drawable.ic_undo_24, R.string.notes_undo) { restoreHistory(undo, redo) })
        formats.addView(iconAction(R.drawable.ic_redo_24, R.string.notes_redo) { restoreHistory(redo, undo) })
        val headingButton = action("H ▾", getString(R.string.notes_format_heading), ink) {}
        headingButton.setOnClickListener {
            PopupMenu(this, headingButton).apply {
                (1..3).forEach { menu.add(0, it, it, "H$it") }
                setOnMenuItemClickListener { prefixLines("#".repeat(it.itemId) + " "); true }
                show()
            }
        }
        formats.addView(headingButton)
        format("B", R.string.notes_format_bold, "**", "**")
        (formats.getChildAt(formats.childCount - 1) as TextView).typeface = Typeface.DEFAULT_BOLD
        format("I", R.string.notes_format_italic, "*", "*")
        (formats.getChildAt(formats.childCount - 1) as TextView).setTypeface(Typeface.DEFAULT, Typeface.ITALIC)
        format("S", R.string.notes_strike, "~~", "~~")
        (formats.getChildAt(formats.childCount - 1) as TextView).paintFlags = android.graphics.Paint.STRIKE_THRU_TEXT_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG
        formats.addView(iconAction(R.drawable.ic_notes_list, R.string.notes_format_list) { toggleList(MarkdownListEditing.Kind.BULLET) })
        formats.addView(action("1. ≡", getString(R.string.notes_numbered), ink) { toggleList(MarkdownListEditing.Kind.NUMBERED) })
        formats.addView(iconAction(R.drawable.ic_notes_checklist, R.string.notes_checklist) { toggleList(MarkdownListEditing.Kind.CHECKBOX) })
        formats.addView(iconAction(R.drawable.ic_notes_quote, R.string.notes_quote) { prefixLines("> ") })
        formats.addView(iconAction(R.drawable.ic_notes_link, R.string.notes_format_link) { insertMarkup("[", "](https://)") })
        formats.addView(iconAction(R.drawable.ic_notes_code, R.string.notes_format_code) { insertMarkup("`", "`") })
        format("{ }", R.string.notes_code_block, "\n```\n", "\n```\n")
        formats.addView(iconAction(R.drawable.ic_notes_table, R.string.notes_table) { insertMarkup("\n| Column | Column |\n| --- | --- |\n| ", " |  |\n") })
        formatBar = HorizontalScrollView(this).apply { addView(formats); isHorizontalScrollBarEnabled = false }
        root.addView(formatBar)
        panes = LinearLayout(this).apply {
            orientation = if (resources.configuration.screenWidthDp >= 600) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        }
        paneDivider = View(this).apply { setBackgroundColor(dividerColor) }
        panes.addView(editor)
        panes.addView(paneDivider)
        panes.addView(previewScroll)
        root.addView(panes, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        if (BuildConfig.DEBUG) root.viewTreeObserver.addOnGlobalFocusChangeListener { old, new ->
            android.util.Log.d("PlektraNotesFocus", "focus ${old?.id} -> ${new?.id} editor=${editor.hasFocus()} heading=${heading.hasFocus()}")
        }
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        heading.doAfterTextChanged { saveNote() }
        lastBody = editor.text.toString()
        editor.doAfterTextChanged {
            if (continuingList) return@doAfterTextChanged
            if (!loadingNote && !restoringHistory) {
                MarkdownListEditing.continueAfterEnter(lastBody, editor.text.toString(), editor.selectionStart)?.let { edit ->
                    continuingList = true
                    editor.text.replace(0, editor.length(), edit.text)
                    editor.setSelection(edit.start)
                    continuingList = false
                }
            }
            val current = editor.text.toString()
            if (!loadingNote && !restoringHistory && current != lastBody) {
                undo.addLast(lastBody)
                if (undo.size > 100) undo.removeFirst()
                redo.clear()
            }
            lastBody = current
            saveNote()
            if (viewMode != ViewMode.MARKDOWN) markwon.setMarkdown(preview, editor.text.toString())
        }
        val focusId = state?.getInt("focus", R.id.notes_body) ?: if (selectedNote.headingFocused) R.id.notes_heading else R.id.notes_body
        restoreHeadingFocus = focusId == R.id.notes_heading
        val target = if (restoreHeadingFocus) heading else editor
        target.requestFocus()
        val selection = selectedNote.cursor.coerceIn(0, target.length())
        target.setSelection(selection)
        val mode = state?.getInt("view_mode") ?: notePrefs.getInt("view_mode", 0)
        setViewMode(ViewMode.entries.getOrElse(mode) { ViewMode.MARKDOWN }, showInput = false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("focus", if (heading.hasFocus()) R.id.notes_heading else R.id.notes_body)
        outState.putInt("view_mode", viewMode.ordinal)
        saveNote()
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        saveNote()
        super.onPause()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        val target = currentFocus as? EditText
        val start = target?.selectionStart
        val end = target?.selectionEnd
        super.onConfigurationChanged(newConfig)
        if (viewMode != ViewMode.PREVIEW && target != null) {
            target.requestFocus()
            target.setSelection((start ?: 0).coerceIn(0, target.length()), (end ?: 0).coerceIn(0, target.length()))
        }
        if (BuildConfig.DEBUG) android.util.Log.d("PlektraNotesFocus", "configuration keyboard=${newConfig.keyboard} hidden=${newConfig.hardKeyboardHidden} focus=${currentFocus?.id}")
    }

    private fun saveNote() {
        if (loadingNote) return
        val target = if (heading.hasFocus() || (viewMode == ViewMode.PREVIEW && restoreHeadingFocus)) heading else editor
        notePrefs.edit().putString("heading", heading.text.toString()).putString("body", editor.text.toString())
            .putInt("focus", target.id).putInt("cursor", target.selectionStart.coerceAtLeast(0))
            .putInt("view_mode", viewMode.ordinal).apply()
        selectedNote = selectedNote.copy(title = heading.text.toString(), body = editor.text.toString(),
            cursor = target.selectionStart.coerceAtLeast(0), headingFocused = target == heading)
        notes.save(selectedNote)
        title = selectedNote.title.ifBlank { getString(R.string.notes_untitled) }
    }

    private fun setViewMode(mode: ViewMode, showInput: Boolean) {
        if (mode == ViewMode.PREVIEW) {
            restoreHeadingFocus = heading.hasFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(editor.windowToken, 0)
            root.isFocusableInTouchMode = true
            root.requestFocus()
        } else {
            root.isFocusableInTouchMode = false
            root.isFocusable = false
        }
        viewMode = mode
        val editing = mode != ViewMode.PREVIEW
        editor.visibility = if (editing) View.VISIBLE else View.GONE
        formatBar.visibility = if (editing) View.VISIBLE else View.GONE
        previewScroll.visibility = if (mode == ViewMode.MARKDOWN) View.GONE else View.VISIBLE
        paneDivider.visibility = if (mode == ViewMode.BOTH) View.VISIBLE else View.GONE
        val horizontal = panes.orientation == LinearLayout.HORIZONTAL
        editor.layoutParams = LinearLayout.LayoutParams(if (horizontal) 0 else -1, if (horizontal) -1 else 0, 1f)
        previewScroll.layoutParams = LinearLayout.LayoutParams(if (horizontal) 0 else -1, if (horizontal) -1 else 0, 1f)
        paneDivider.layoutParams = LinearLayout.LayoutParams(if (horizontal) dp(1) else -1, if (horizontal) -1 else dp(1))
        modeTabs.forEachIndexed { index, tab ->
            val selected = index == mode.ordinal
            tab.isSelected = selected
            tab.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            tab.setTextColor(if (selected) accentColor else inkColor)
            (tab.text as? android.text.Spanned)?.getSpans(0, tab.length(), android.text.style.ImageSpan::class.java)
                ?.forEach { it.drawable.setTint(if (selected) accentColor else inkColor) }
            tab.background = if (selected) LayerDrawable(arrayOf(GradientDrawable().apply { setColor(accentColor) })).apply {
                setLayerGravity(0, Gravity.BOTTOM)
                setLayerHeight(0, dp(2))
            } else null
        }
        if (mode != ViewMode.MARKDOWN) markwon.setMarkdown(preview, editor.text.toString())
        saveNote()
        if (editing && showInput) {
            val target = if (restoreHeadingFocus) heading else editor
            target.requestFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(target, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun insertMarkup(before: String, after: String) {
        val start = minOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
        val end = maxOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(start)
        val selected = editor.text.subSequence(start, end).toString()
        editor.text.replace(start, end, before + selected + after)
        editor.requestFocus()
        editor.setSelection(start + before.length, start + before.length + selected.length)
    }

    private fun toggleList(kind: MarkdownListEditing.Kind) {
        val edit = MarkdownListEditing.toggle(editor.text.toString(), editor.selectionStart, editor.selectionEnd, kind)
        editor.text.replace(0, editor.length(), edit.text)
        editor.requestFocus()
        editor.setSelection(edit.start, edit.end)
    }

    private fun togglePreviewCheckbox(span: TaskListSpan) {
        val rendered = preview.text as? android.text.Spanned ?: return
        val spans = rendered.getSpans(0, rendered.length, TaskListSpan::class.java).sortedBy { rendered.getSpanStart(it) }
        val lines = MarkdownListEditing.checkboxLines(editor.text.toString())
        if (lines.size != spans.size) return
        val line = lines.getOrNull(spans.indexOf(span)) ?: return
        val start = editor.selectionStart
        val end = editor.selectionEnd
        editor.text.replace(0, editor.length(), MarkdownListEditing.toggleCheckbox(editor.text.toString(), line))
        editor.setSelection(start.coerceAtLeast(0), end.coerceAtLeast(0))
    }

    private fun showNotes() {
        saveNote()
        val items = notes.all()
        AlertDialog.Builder(this).setTitle(R.string.notes_title)
            .setItems(items.map { it.title.ifBlank { getString(R.string.notes_untitled) } }.toTypedArray()) { _, index ->
                switchNote(items[index])
            }.setPositiveButton(R.string.notes_new) { _, _ -> switchNote(notes.create(), focusHeading = true) }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun switchNote(note: NotesRepository.Note, focusHeading: Boolean = note.headingFocused) {
        saveNote()
        loadingNote = true
        selectedNote = note
        notes.select(note.id)
        heading.setText(note.title)
        editor.setText(note.body)
        undo.clear()
        redo.clear()
        restoreHeadingFocus = focusHeading
        val target = if (focusHeading) heading else editor
        target.requestFocus()
        target.setSelection(note.cursor.coerceIn(0, target.length()))
        loadingNote = false
        setViewMode(if (viewMode == ViewMode.PREVIEW) ViewMode.MARKDOWN else viewMode, showInput = true)
    }

    private fun restoreHistory(from: ArrayDeque<String>, to: ArrayDeque<String>) {
        if (from.isEmpty()) return
        to.addLast(editor.text.toString())
        restoringHistory = true
        editor.setText(from.removeLast())
        editor.setSelection(editor.length())
        restoringHistory = false
        editor.requestFocus()
    }

    private fun tintedIcon(resource: Int) = getDrawable(resource)?.mutate()?.apply {
        setTint(inkColor)
        setBounds(0, 0, dp(20), dp(20))
    }

    private fun iconAction(icon: Int, description: Int, onClick: () -> Unit): TextView =
        action("", getString(description), inkColor, onClick).apply {
            background = LayerDrawable(arrayOf(tintedIcon(icon))).apply {
                setLayerSize(0, dp(24), dp(24))
                setLayerGravity(0, Gravity.CENTER)
            }
            minimumWidth = dp(48)
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        }

    private fun prefixLines(prefix: String) {
        val start = minOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
        val end = maxOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(start)
        val lineStart = editor.text.toString().lastIndexOf('\n', (start - 1).coerceAtLeast(-1)) + 1
        val content = editor.text.subSequence(lineStart, end).toString()
        editor.text.replace(lineStart, end, prefix + content.replace("\n", "\n$prefix"))
        editor.requestFocus()
        editor.setSelection(end + prefix.length * (content.count { it == '\n' } + 1))
    }

    private fun action(label: String, description: String, color: Int, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        contentDescription = description
        textSize = 14f
        includeFontPadding = false
        setTextColor(color)
        gravity = Gravity.CENTER
        minHeight = dp(48)
        setPadding(dp(12), 0, dp(12), 0)
        isClickable = true
        isFocusable = false
        tooltipText = description
        setOnClickListener { onClick() }
    }

    protected fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
