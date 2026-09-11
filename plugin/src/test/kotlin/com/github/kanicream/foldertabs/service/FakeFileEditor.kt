package com.github.kanicream.foldertabs.service

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel

/** A [FileEditor] the service never attached a header to: the platform's editor for a tool window, say. */
class FakeFileEditor(private val file: VirtualFile) : UserDataHolderBase(), FileEditor {
    private val panel = JPanel()
    override fun getComponent(): JComponent = panel
    override fun getPreferredFocusedComponent(): JComponent? = null
    override fun getName(): String = "fake"
    override fun setState(state: FileEditorState) = Unit
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = true
    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit
    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit
    override fun dispose() = Unit
    override fun getFile(): VirtualFile = file
}
