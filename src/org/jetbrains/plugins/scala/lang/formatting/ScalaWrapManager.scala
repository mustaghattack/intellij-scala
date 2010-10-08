package org.jetbrains.plugins.scala.lang.formatting

import settings.ScalaCodeStyleSettings
import com.intellij.lang.ASTNode
import com.intellij.formatting.{WrapType, Wrap}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScPattern, ScCompositePattern, ScPatternArgumentList, ScInfixPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSequenceArg, ScInfixTypeElement}

/**
 * @author Alexander Podkhalyuzin
 */

object ScalaWrapManager {
  def suggestedWrap(block: ScalaBlock, scalaSettings: ScalaCodeStyleSettings): Wrap = {
    val settings = block.getSettings
    val node = block.getNode
    val psi = node.getPsi
    def wrapBinary(elementMatch: PsiElement => Boolean,
                   elementOperation: PsiElement => PsiElement,
                   assignments: Boolean): Wrap = {
      psi.getParent match {
        case parent: PsiElement if elementMatch(parent) => {
          import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils.priority
          val parentPriority = priority(elementOperation(parent).getText, assignments)
          val childPriority = priority(elementOperation(psi).getText, assignments)
          val notSamePriority = parentPriority != childPriority
          if (notSamePriority) {
            return Wrap.createChildWrap(block.getWrap,
                                        WrapType.byLegacyRepresentation(settings.BINARY_OPERATION_WRAP),
                                        false)
          }
          else return Wrap.createWrap(settings.BINARY_OPERATION_WRAP, false)
        }
        case _ => return Wrap.createWrap(settings.BINARY_OPERATION_WRAP, false)
      }
    }

    psi match {
      case psi: ScInfixExpr => {
        return wrapBinary(_.isInstanceOf[ScInfixExpr], _.asInstanceOf[ScInfixExpr].operation, true)
      }
      case psi: ScInfixPattern => {
        return wrapBinary(_.isInstanceOf[ScInfixPattern], _.asInstanceOf[ScInfixPattern].refernece, false)
      }
      case psi: ScInfixTypeElement => {
        return wrapBinary(_.isInstanceOf[ScInfixTypeElement], _.asInstanceOf[ScInfixTypeElement].ref, false)
      }
      case psi: ScCompositePattern => {
        return Wrap.createWrap(settings.BINARY_OPERATION_WRAP, false)
      }
      case psi: ScArgumentExprList => {
        val parentSuggestedWrap = block.myParentBlock.suggestedWrap
        val wrap = if (parentSuggestedWrap != null) Wrap.createChildWrap(parentSuggestedWrap,
                                        WrapType.byLegacyRepresentation(settings.CALL_PARAMETERS_WRAP), false)
        else Wrap.createWrap(settings.CALL_PARAMETERS_WRAP, false)
        if (settings.PREFER_PARAMETERS_WRAP) {
          wrap.ignoreParentWraps
        }
        return wrap
      }
      case psi: ScReferenceExpression => {
        return Wrap.createWrap(settings.METHOD_CALL_CHAIN_WRAP, true)
      }
      case psi: ScMethodCall => {
        return Wrap.createWrap(settings.METHOD_CALL_CHAIN_WRAP, true)
      }
      case psi: ScPatternArgumentList => {
        return Wrap.createWrap(settings.CALL_PARAMETERS_WRAP, false)
      }
      case _ =>
    }
    return null
  }

  def arrangeSuggestedWrapForChild(parent: ScalaBlock, child: ASTNode, settings: ScalaCodeStyleSettings,
                                   suggestedWrap: Wrap): Wrap = {
    val parentNode = parent.getNode
    val parentPsi = parentNode.getPsi
    val childPsi = child.getPsi

    def arrageBinary(elementMatch: PsiElement => Boolean,
                     elementOperation: PsiElement => PsiElement,
                     elementRightSide: PsiElement => PsiElement,
                     elementLeftSide: PsiElement => PsiElement): Wrap = {
      childPsi.getParent match {
        case parent: PsiElement if elementMatch(parent) => {
          if (elementOperation(parent) == childPsi) return null
          if (parent != parentPsi) return suggestedWrap
          else if (elementLeftSide(parentPsi) == childPsi) return suggestedWrap
          else if (elementRightSide(parentPsi) == childPsi) return suggestedWrap
          else return null
        }
        case _ => return null //hasn't to be
      }
    }

    parentPsi match {
      case inf: ScInfixExpr => {
        return arrageBinary(_.isInstanceOf[ScInfixExpr], _.asInstanceOf[ScInfixExpr].operation,
                            _.asInstanceOf[ScInfixExpr].rOp, _.asInstanceOf[ScInfixExpr].lOp)
      }
      case inf: ScInfixPattern => {
        return arrageBinary(_.isInstanceOf[ScInfixPattern], _.asInstanceOf[ScInfixPattern].refernece,
                            _.asInstanceOf[ScInfixPattern].rightPattern.getOrElse(null),
                            _.asInstanceOf[ScInfixPattern].leftPattern)
      }
      case inf: ScInfixTypeElement => {
        return arrageBinary(_.isInstanceOf[ScInfixTypeElement], _.asInstanceOf[ScInfixTypeElement].ref,
                            _.asInstanceOf[ScInfixTypeElement].rOp.getOrElse(null),
                            _.asInstanceOf[ScInfixTypeElement].lOp)
      }
      case psi: ScCompositePattern => {
        if (childPsi.isInstanceOf[ScPattern]) return suggestedWrap
        else return null
      }
      case call: ScMethodCall => {
        if (child.getElementType == ScalaTokenTypes.tDOT) return suggestedWrap
        else return null
      }
      case ref: ScReferenceExpression => {
        if (child.getElementType == ScalaTokenTypes.tDOT) return suggestedWrap
        else return null
      }
      case args: ScArgumentExprList => {
        if (childPsi.isInstanceOf[ScExpression]) return suggestedWrap
        else return null
      }
      case patt: ScPatternArgumentList => {
        if (childPsi.isInstanceOf[ScPattern]) return suggestedWrap
        else if (childPsi.isInstanceOf[ScSequenceArg]) return suggestedWrap
        else return null
      }
      case _ =>
    }
    return null
  }
}