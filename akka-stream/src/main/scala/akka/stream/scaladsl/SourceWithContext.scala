/*
 * Copyright (C) 2014-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.stream.scaladsl

import scala.annotation.unchecked.uncheckedVariance

import akka.annotation.ApiMayChange
import akka.stream._

/**
 * A source that automatically propagates a context with an element.
 *
 * Can be created by calling [[Source.startContextPropagation()]]
 *
 * API MAY CHANGE
 */
@ApiMayChange
final class SourceWithContext[+Ctx, +Out, +Mat] private[stream] (
  delegate: Source[(Out, Ctx), Mat]
) extends GraphDelegate(delegate) with FlowWithContextOps[Ctx, Out, Mat] {
  override type ReprMat[+C, +O, +M] = SourceWithContext[C, O, M @uncheckedVariance]

  override def via[Ctx2, Out2, Mat2](viaFlow: Graph[FlowShape[(Out, Ctx), (Out2, Ctx2)], Mat2]): Repr[Ctx2, Out2] =
    new SourceWithContext(delegate.via(viaFlow))

  override def viaMat[Ctx2, Out2, Mat2, Mat3](flow: Graph[FlowShape[(Out, Ctx), (Out2, Ctx2)], Mat2])(combine: (Mat, Mat2) ⇒ Mat3): SourceWithContext[Ctx2, Out2, Mat3] =
    new SourceWithContext(delegate.viaMat(flow)(combine))

  /**
   * Stops automatic context propagation from here and converts this to a regular
   * stream of a pair of (data, context).
   */
  def endContextPropagation: Source[(Out, Ctx), Mat] = delegate

  def asJava[JCtx >: Ctx, JOut >: Out, JMat >: Mat]: javadsl.SourceWithContext[JCtx, JOut, JMat] =
    new javadsl.SourceWithContext(this)
}

