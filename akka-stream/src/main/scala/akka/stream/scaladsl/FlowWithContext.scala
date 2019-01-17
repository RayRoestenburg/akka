/*
 * Copyright (C) 2014-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.stream.scaladsl

import scala.annotation.unchecked.uncheckedVariance
import akka.annotation.ApiMayChange
import akka.stream._

/**
 * API MAY CHANGE
 */
@ApiMayChange
object FlowWithContext {
  def apply[Ctx, In]: FlowWithContext[Ctx, In, Ctx, In, akka.NotUsed] = {
    val under = Flow[(In, Ctx)]
    new FlowWithContext[Ctx, In, Ctx, In, akka.NotUsed](under)
  }
  def from[CI, I, CO, O, M](flow: Flow[(I, CI), (O, CO), M]) = new FlowWithContext(flow)
}

/**
 * API MAY CHANGE
 */
@ApiMayChange
final class FlowWithContext[-CtxIn, -In, +CtxOut, +Out, +Mat](
  delegate: Flow[(In, CtxIn), (Out, CtxOut), Mat]
) extends GraphDelegate(delegate) with FlowWithContextOps[CtxOut, Out, Mat] {
  override type ReprMat[+C, +O, +M] = FlowWithContext[CtxIn @uncheckedVariance, In @uncheckedVariance, C, O, M @uncheckedVariance]

  override def via[Ctx2, Out2, Mat2](viaFlow: Graph[FlowShape[(Out, CtxOut), (Out2, Ctx2)], Mat2]): Repr[Ctx2, Out2] =
    FlowWithContext.from(delegate.via(viaFlow))

  override def viaMat[Ctx2, Out2, Mat2, Mat3](flow: Graph[FlowShape[(Out, CtxOut), (Out2, Ctx2)], Mat2])(combine: (Mat, Mat2) ⇒ Mat3): FlowWithContext[CtxIn, In, Ctx2, Out2, Mat3] =
    FlowWithContext.from(delegate.viaMat(flow)(combine))

  def asFlow: Flow[(In, CtxIn), (Out, CtxOut), Mat] = delegate

  def asJava[JCtxIn <: CtxIn, JIn <: In, JCtxOut >: CtxOut, JOut >: Out, JMat >: Mat]: javadsl.FlowWithContext[JCtxIn, JIn, JCtxOut, JOut, JMat] =
    new javadsl.FlowWithContext(this)
}
