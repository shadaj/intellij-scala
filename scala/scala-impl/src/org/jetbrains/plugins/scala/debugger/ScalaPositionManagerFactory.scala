package org.jetbrains.plugins.scala.debugger

import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.{PositionManager, PositionManagerFactory}
import org.jetbrains.plugins.scala.extensions.invokeLater
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}

/**
 * User: Alefas
 * Date: 14.10.11
 */
class ScalaPositionManagerFactory extends PositionManagerFactory {
  def createPositionManager(process: DebugProcess): PositionManager = {
    invokeLater {
      Stats.trigger(process.getProject.hasScala, FeatureKey.debuggerTotal)
    }
    new ScalaPositionManager(process)
  }
}