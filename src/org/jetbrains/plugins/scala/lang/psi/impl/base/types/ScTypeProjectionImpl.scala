package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.util.IncorrectOperationException
import lexer.ScalaTokenTypes
import psi.ScalaPsiElementImpl
import api.base.types._
import psi.types._
import com.intellij.lang.ASTNode
import lang.resolve._
import processor.{CompletionProcessor, ResolveProcessor, BaseProcessor}
import result.{TypeResult, Failure, Success, TypingContext}
import com.intellij.psi._
import api.ScalaElementVisitor
import api.toplevel.imports.ScImportStmt
import completion.lookups.LookupElementManager
import extensions.toPsiMemberExt

/**
* @author Alexander Podkhalyuzin
* Date: 13.03.2008
*/
class ScTypeProjectionImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScTypeProjection {
  override def toString: String = "TypeProjection"

  protected def innerType(ctx: TypingContext) = {
    bind() match {
      case Some(ScalaResolveResult(elem, subst)) => {
        val te: TypeResult[ScType] = typeElement.getType(ctx)
        te match {
          case Success(ScDesignatorType(pack: PsiPackage), a) => {
            Success(ScType.designator(elem), Some(this))
          }
          case _ =>
            te map {ScProjectionType(_, elem, superReference = false)}
        }
      }
      case _ => Failure("Cannot Resolve reference", Some(this))
    }
  }

  def getKinds(incomplete: Boolean, completion: Boolean) = StdKinds.stableClass

  def multiResolve(incomplete: Boolean) =
    ResolveCache.getInstance(getProject).resolveWithCaching(this, MyResolver, true, incomplete)

  def getVariants: Array[Object] = {
    val isInImport: Boolean = ScalaPsiUtil.getParentOfType(this, classOf[ScImportStmt]) != null
    doResolve(new CompletionProcessor(getKinds(incomplete = true), this)).flatMap {
      case res: ScalaResolveResult =>
        import org.jetbrains.plugins.scala.lang.psi.types.Nothing
        val qualifier = res.fromType.getOrElse(Nothing)
        LookupElementManager.getLookupElement(res, isInImport = isInImport, qualifierType = qualifier, isInStableCodeReference = false)
      case r => Seq(r.getElement)
    }
  }

  def bindToElement(p1: PsiElement) = throw new IncorrectOperationException("NYI")
  def nameId = findChildByType(ScalaTokenTypes.tIDENTIFIER)
  def qualifier = None

  object MyResolver extends ResolveCache.PolyVariantResolver[ScTypeProjectionImpl] {
    def resolve(projection: ScTypeProjectionImpl, incomplete: Boolean) = {
      projection.doResolve(new ResolveProcessor(projection.getKinds(incomplete), projection, projection.refName))
    }
  }

  def doResolve(processor: BaseProcessor, accessibilityCheck: Boolean = true): Array[ResolveResult] = {
    if (!accessibilityCheck) processor.doNotCheckAccessibility()
    val projected = typeElement.getType(TypingContext.empty).getOrAny
    processor.processType(projected, this)
    val res = processor.candidates.map {r : ScalaResolveResult =>
      r.element match {
        case mem: PsiMember if mem.containingClass != null =>
          new ScalaResolveResult(mem, r.substitutor/*.bindO(mem.getContainingClass, projected, this)*/, r.importsUsed)
        case _ => r : ResolveResult
      }
    }
    if (accessibilityCheck && res.length == 0) return doResolve(processor, accessibilityCheck = false)
    res
  }

  def getSameNameVariants: Array[ResolveResult] = doResolve(new CompletionProcessor(getKinds(incomplete = true), this, false, Some(refName)))

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitTypeProjection(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case s: ScalaElementVisitor => s.visitTypeProjection(this)
      case _ => super.accept(visitor)
    }
  }
}