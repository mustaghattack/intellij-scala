package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import builder.ScalaPsiBuilder
import annotation.tailrec
import util.ParserUtils
import com.intellij.psi.tree.IElementType

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * Block ::= {BlockStat semi}[ResultExpr]
 */

object Block {

  def parse(builder: ScalaPsiBuilder) {
    while (!ResultExpr.parse(builder) && BlockStat.parse(builder)) {
      val rollMarker = builder.mark
      if (!ResultExpr.parse(builder) && BlockStat.parse(builder)) {
        rollMarker.rollbackTo()
        builder.getTokenType match {
          case ScalaTokenTypes.tSEMICOLON => {
            builder.advanceLexer()
          }
          case _ => {
            if (!builder.newlineBeforeCurrentToken)
              builder error ErrMsg("semi.expected")
          }
        }
      } else {
        rollMarker.rollbackTo()
      }
    }
  }

  private def parseImpl(builder: ScalaPsiBuilder): Int = {
    var i: Int = 0

    var tts: List[IElementType] = Nil
    var continue = true

    while (continue) {
      if (ResultExpr.parse(builder)) {
        continue = false
        i = i + 1
        tts ::= builder.getTokenType
      } else {
        if (BlockStat.parse(builder)) {
          i = i + 1
          tts ::= builder.getTokenType
        } else {
          continue = false
        }
      }
    }
    if (tts.drop(1).headOption == Some(ScalaTokenTypes.tSEMICOLON)) i -= 1  // See unit_to_unit.test

    i
  }

  def parse(builder: ScalaPsiBuilder, hasBrace: Boolean): Boolean = parse(builder, hasBrace, needNode = false)

  def parse(builder: ScalaPsiBuilder, hasBrace: Boolean, needNode: Boolean): Boolean = {
    if (hasBrace) {
      val blockMarker = builder.mark
      builder.getTokenType match {
        case ScalaTokenTypes.tLBRACE => {
          builder.advanceLexer()
          builder.enableNewlines
        }
        case _ => {
          blockMarker.drop()
          return false
        }
      }
      ParserUtils.parseLoopUntilRBrace(builder, () => parse(builder))
      builder.restoreNewlinesState
      blockMarker.done(ScalaElementTypes.BLOCK_EXPR)
    }
    else {
      val bm = builder.mark()
      val count = parseImpl(builder)
      if (count > 1) {
        bm.done(ScalaElementTypes.BLOCK)
      } else {
        if (!needNode) bm.drop() else bm.done(ScalaElementTypes.BLOCK)
//        bm.done(ScalaElementTypes.BLOCK)
      }
    }
    true
  }
}