package org.jetbrains.plugins.scala.codeInsight.implicits

import com.intellij.openapi.actionSystem.{AnActionEvent, CommonDataKeys, ToggleAction}

class ExpandImplicitHintsAction extends ToggleAction {
  override def update(e: AnActionEvent): Unit = {
    e.getPresentation.setEnabled(ImplicitHints.enabled)
  }

  override def isSelected(e: AnActionEvent): Boolean = ImplicitHints.expanded

  override def setSelected(e: AnActionEvent, state: Boolean): Unit = {
    ImplicitHints.expanded = state

    val editor = e.getData(CommonDataKeys.EDITOR)

    if (state) {
      ImplicitHints.expandIn(editor)
    } else {
      ImplicitHints.collapseIn(editor)
    }
  }
}

object ExpandImplicitHintsAction {
  val Id = "Scala.ExpandImplicits"
}
