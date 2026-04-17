package org.openclover.idea.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.openclover.idea.CloverProjectService
import org.openclover.idea.coverage.CoverageState
import org.openclover.idea.coverage.LineCoverageStatus
import java.awt.Color

/**
 * Annotates open editors with coverage data: line background colors
 * and gutter icons showing covered/uncovered/partial status.
 *
 * Listens to [CoverageState] changes and updates all open editors
 * when new coverage data is loaded.
 */
class CoverageEditorAnnotator(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
) : Disposable {

    private val activeHighlighters = mutableMapOf<Editor, MutableList<RangeHighlighter>>()

    fun start() {
        // Listen for new editors opening
        EditorFactory.getInstance().addEditorFactoryListener(editorListener, this)

        // Listen for file tab switches — editors are reused, not created/destroyed
        project.messageBus.connect(this).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            fileEditorListener,
        )

        // React to coverage state changes — must dispatch UI work to EDT
        val service = CloverProjectService.getInstance(project)
        coroutineScope.launch {
            service.coverageManager.state.collectLatest { state ->
                ApplicationManager.getApplication().invokeLater {
                    if (project.isDisposed) return@invokeLater
                    when (state) {
                        is CoverageState.Loaded -> annotateAllEditors(state)
                        else -> clearAllEditors()
                    }
                }
            }
        }
    }

    private fun annotateAllEditors(state: CoverageState.Loaded) {
        for (editor in EditorFactory.getInstance().allEditors) {
            if (editor.project != project) continue
            annotateEditor(editor, state)
        }
    }

    private fun annotateEditor(editor: Editor, state: CoverageState.Loaded) {
        clearEditor(editor)

        val document = editor.document
        val virtualFile = FileDocumentManager.getInstance().getFile(document) ?: return
        val filePath = virtualFile.canonicalPath ?: virtualFile.path
        val fileCoverage = state.fileCoverage[filePath] ?: return

        val highlighters = mutableListOf<RangeHighlighter>()
        val markupModel = editor.markupModel

        for ((line, status) in fileCoverage.lineStatuses) {
            val lineIndex = line - 1 // Convert 1-based to 0-based
            if (lineIndex < 0 || lineIndex >= document.lineCount) continue

            val startOffset = document.getLineStartOffset(lineIndex)
            val endOffset = document.getLineEndOffset(lineIndex)

            val attrs = textAttributesFor(status)
            val highlighter = markupModel.addRangeHighlighter(
                startOffset,
                endOffset,
                COVERAGE_HIGHLIGHTER_LAYER,
                attrs,
                HighlighterTargetArea.LINES_IN_RANGE,
            )

            highlighter.gutterIconRenderer = CoverageGutterRenderer(status, fileCoverage, line)
            highlighters.add(highlighter)
        }

        activeHighlighters[editor] = highlighters
    }

    private fun clearAllEditors() {
        for (editor in activeHighlighters.keys.toList()) {
            clearEditor(editor)
        }
    }

    private fun clearEditor(editor: Editor) {
        val highlighters = activeHighlighters.remove(editor) ?: return
        val markupModel = editor.markupModel
        for (h in highlighters) {
            markupModel.removeHighlighter(h)
        }
    }

    override fun dispose() {
        clearAllEditors()
    }

    /**
     * Handles file tab switches via selectionChanged (fires on every tab switch).
     * fileOpened only fires on first open, not tab switches.
     */
    private val fileEditorListener = object : FileEditorManagerListener {
        override fun selectionChanged(event: FileEditorManagerEvent) {
            val newFile = event.newFile ?: return
            val state = CloverProjectService.getInstance(project).coverageManager.state.value
            if (state is CoverageState.Loaded) {
                for (editor in EditorFactory.getInstance().allEditors) {
                    if (editor.project != project) continue
                    val editorFile = FileDocumentManager.getInstance().getFile(editor.document)
                    if (editorFile == newFile) {
                        annotateEditor(editor, state)
                    }
                }
            }
        }
    }

    private val editorListener = object : EditorFactoryListener {
        override fun editorCreated(event: EditorFactoryEvent) {
            val editor = event.editor
            if (editor.project != project) return
            val state = CloverProjectService.getInstance(project).coverageManager.state.value
            if (state is CoverageState.Loaded) {
                annotateEditor(editor, state)
            }
        }

        override fun editorReleased(event: EditorFactoryEvent) {
            clearEditor(event.editor)
        }
    }

    companion object {
        /** Layer for coverage highlights — below error highlights but above syntax. */
        private const val COVERAGE_HIGHLIGHTER_LAYER = HighlighterLayer.FIRST + 1

        private val COVERED_BG = JBColor(Color(0xE6, 0xF5, 0xE6), Color(0x2A, 0x40, 0x2A))
        private val UNCOVERED_BG = JBColor(Color(0xFC, 0xE4, 0xE4), Color(0x50, 0x2A, 0x2A))
        private val PARTIAL_BG = JBColor(Color(0xFF, 0xF3, 0xCD), Color(0x4A, 0x40, 0x2A))

        fun textAttributesFor(status: LineCoverageStatus): TextAttributes {
            val attrs = TextAttributes()
            attrs.backgroundColor = when (status) {
                LineCoverageStatus.COVERED -> COVERED_BG
                LineCoverageStatus.UNCOVERED -> UNCOVERED_BG
                LineCoverageStatus.PARTIAL -> PARTIAL_BG
            }
            return attrs
        }
    }
}
