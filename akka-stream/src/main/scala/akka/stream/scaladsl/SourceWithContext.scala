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
object SourceWithContext {
  def apply[Out, Mat](underlying: Source[Out, Mat]): SourceWithContext[Out, Out, Mat] = {
    val under = underlying.map(e ⇒ (e, e))
    new SourceWithContext[Out, Out, Mat](under)
  }
  def from[Out, Ctx, Mat](under: Source[(Out, Ctx), Mat]): SourceWithContext[Ctx, Out, Mat] = {
    new SourceWithContext[Ctx, Out, Mat](under)
  }
}

/**
 * API MAY CHANGE
 */
@ApiMayChange
final class SourceWithContext[+Ctx, +Out, +Mat](
  delegate: Source[(Out, Ctx), Mat]
) extends GraphDelegate(delegate) with FlowWithContextOps[Ctx, Out, Mat] {

  override type ReprMat[+C, +O, +M] = SourceWithContext[C, O, M @uncheckedVariance]

  override def via[Ctx2, Out2, Mat2](viaFlow: Graph[FlowShape[(Out, Ctx), (Out2, Ctx2)], Mat2]): Repr[Ctx2, Out2] =
    SourceWithContext.from(delegate.via(viaFlow))

  override def viaMat[Ctx2, Out2, Mat2, Mat3](flow: Graph[FlowShape[(Out, Ctx), (Out2, Ctx2)], Mat2])(combine: (Mat, Mat2) ⇒ Mat3): SourceWithContext[Ctx2, Out2, Mat3] =
    SourceWithContext.from(delegate.viaMat(flow)(combine))

  def to[Mat2](sink: Graph[SinkShape[(Out, Ctx)], Mat2]): RunnableGraph[Mat] = delegate.toMat(sink)(Keep.left)

  def toMat[Mat2, Mat3](sink: Graph[SinkShape[(Out, Ctx)], Mat2])(combine: (Mat, Mat2) ⇒ Mat3): RunnableGraph[Mat3] =
    delegate.toMat(sink)(combine)

  /**
   * Stops automatic context propagation from here and converts this to a regular
   * stream of a pair of (data, context).
   */
  def endContextPropagation: Source[(Out, Ctx), Mat] = delegate

  def asJava[JCtx >: Ctx, JOut >: Out, JMat >: Mat]: javadsl.SourceWithContext[JCtx, JOut, JMat] =
    new javadsl.SourceWithContext(this)
}

