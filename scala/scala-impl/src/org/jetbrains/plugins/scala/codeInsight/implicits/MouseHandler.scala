package org.jetbrains.plugins.scala.codeInsight.implicits

import java.awt.event.{KeyAdapter, KeyEvent, MouseEvent}
import java.awt.{Cursor, Point}

import com.intellij.codeInsight.hint.{HintManager, HintManagerImpl, HintUtil}
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.editor.event._
import com.intellij.openapi.editor.{Editor, EditorFactory, Inlay}
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.LightweightHint
import com.intellij.util.ui.{JBUI, UIUtil}
import org.jetbrains.plugins.scala.extensions.ObjectExt

class MouseHandler(project: Project,
                   startupManager: StartupManager,
                   editorFactory: EditorFactory) extends AbstractProjectComponent(project) {

  private var activeHyperlink = Option.empty[(Inlay, Text)]

  private val mousePressListener = new EditorMouseAdapter {
    override def mousePressed(e: EditorMouseEvent): Unit = {
      MouseHandler.mousePressLocation = e.getMouseEvent.getPoint
    }

    override def mouseClicked(e: EditorMouseEvent): Unit = {
      if (!e.isConsumed && project.isInitialized && !project.isDisposed) {
        if (e.getMouseEvent.getButton == MouseEvent.BUTTON1) {
          if (SystemInfo.isMac && e.getMouseEvent.isMetaDown || e.getMouseEvent.isControlDown) {
            activeHyperlink.foreach { case (_, text) =>
              e.consume()
              deactivateActiveHypelink(e.getEditor)
              CommandProcessor.getInstance.executeCommand(project,
                () => text.navigatable.filter(_.canNavigate).foreach(_.navigate(true)), null, null)
            }
          } else {
            expandableAt(e.getEditor, e.getMouseEvent.getPoint).foreach { case (inlay, text) =>
              inlay.getRenderer.asOptionOf[TextRenderer].foreach { renderer =>
                renderer.expand(text)
                inlay.updateSize()
              }
            }
          }
        }
      }
    }
  }

  private val mouseMovedListener = new EditorMouseMotionAdapter {
    override def mouseMoved(e: EditorMouseEvent): Unit = {
      if (!e.isConsumed && project.isInitialized && !project.isDisposed) {
        if (SystemInfo.isMac && e.getMouseEvent.isMetaDown || e.getMouseEvent.isControlDown) {
          hyperlinkAt(e.getEditor, e.getMouseEvent.getPoint) match {
            case Some((inlay, text)) if activeHyperlink.contains((inlay, text)) =>
            // the hyperlink is already activated
            case Some((inlay, text)) =>
              deactivateActiveHypelink(e.getEditor)
              activateHyperlink(e.getEditor, inlay, text)
              text.tooltip.foreach(showTooltip(e.getEditor, e.getMouseEvent, _))
            case None =>
              deactivateActiveHypelink(e.getEditor)
          }
        } else {
          deactivateActiveHypelink(e.getEditor)
        }
      }
    }
  }

  startupManager.registerPostStartupActivity(() => {
    val multicaster = editorFactory.getEventMulticaster
    multicaster.addEditorMouseListener(mousePressListener, project)
    multicaster.addEditorMouseMotionListener(mouseMovedListener, project)
  })

  private def activateHyperlink(editor: Editor, inlay: Inlay, text: Text): Unit = {
    text.hyperlink = true
    activeHyperlink = Some(inlay, text)
    inlay.repaint()
    UIUtil.setCursor(editor.getContentComponent, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))

    editor.getContentComponent.addKeyListener(new KeyAdapter {
      override def keyPressed(keyEvent: KeyEvent): Unit = {
        // Why, in Windows, Control key press events are generated on mouse movement?
        if (keyEvent.getKeyCode != KeyEvent.VK_CONTROL) {
          handle()
        }
      }

      override def keyReleased(keyEvent: KeyEvent): Unit = handle()

      private def handle(): Unit = {
        editor.getContentComponent.removeKeyListener(this)
        deactivateActiveHypelink(editor)
      }
    })
  }

  private def deactivateActiveHypelink(editor: Editor): Unit = {
    activeHyperlink.foreach { case (inlay, text) =>
      text.hyperlink = false
      inlay.repaint()
      editor.getContentComponent.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR))
    }
    activeHyperlink = None
  }

  private def expandableAt(editor: Editor, point: Point): Option[(Inlay, Text)] = textAt(editor, point).filter {
    case (_, text) => text.expansion.isDefined
  }

  private def hyperlinkAt(editor: Editor, point: Point): Option[(Inlay, Text)] = textAt(editor, point).filter {
    case (_, text) => text.navigatable.isDefined
  }

  private def textAt(editor: Editor, point: Point): Option[(Inlay, Text)] =
    Option(editor.getInlayModel.getElementAt(point)).flatMap { inlay =>
      inlay.getRenderer.asOptionOf[TextRenderer].flatMap { renderer =>
        val inlayPoint = editor.visualPositionToXY(inlay.getVisualPosition)
        renderer.textAt(editor, point.x - inlayPoint.x).map((inlay, _))
      }
    }

  private def showTooltip(editor: Editor, e: MouseEvent, text: String): Unit = {
    val hint = {
      val label = HintUtil.createInformationLabel(text)
      label.setBorder(JBUI.Borders.empty(6, 6, 5, 6))
      new LightweightHint(label)
    }

    val constraint = HintManager.ABOVE

    val point = {
      val p = HintManagerImpl.getHintPosition(hint, editor,
        editor.xyToVisualPosition(e.getPoint), constraint)
      p.x = e.getXOnScreen - editor.getContentComponent.getTopLevelAncestor.getLocationOnScreen.x
      p
    }

    val manager = HintManagerImpl.getInstanceImpl

    manager.showEditorHint(hint, editor, point,
      HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING, 0, false,
      HintManagerImpl.createHintHint(editor, point, hint, constraint).setContentActive(false))
  }
}

object MouseHandler {
  var mousePressLocation: Point = new Point(0, 0)
}
