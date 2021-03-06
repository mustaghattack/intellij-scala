package org.jetbrains.plugins.scala
package codeInspection
package unusedInspections

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.codeHighlighting.{Pass, TextEditorHighlightingPassRegistrar, TextEditorHighlightingPass, TextEditorHighlightingPassFactory}
import com.intellij.codeInsight.daemon.impl.FileStatusMap
import com.intellij.openapi.project.Project

class ScalaUnusedSymbolPassFactory(project: Project)
        extends TextEditorHighlightingPassFactory {
  TextEditorHighlightingPassRegistrar.getInstance(project).
    registerTextEditorHighlightingPass(this, Array[Int](Pass.UPDATE_ALL), null, false, -1)

  def projectClosed() {}

  def projectOpened() {}

  def createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass = {
    new ScalaUnusedSymbolPass(file, editor)
  }

  def initComponent() {}

  def disposeComponent() {}

  def getComponentName: String = "Scala Unused symbol pass factory"
}