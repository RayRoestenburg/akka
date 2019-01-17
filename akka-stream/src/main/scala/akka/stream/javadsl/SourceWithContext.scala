/*
 * Copyright (C) 2014-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.stream.javadsl

import akka.annotation.ApiMayChange
import akka.japi.{ Pair, Util, function }
import akka.stream._

import scala.annotation.unchecked.uncheckedVariance
import scala.collection.JavaConverters._
import java.util.concurrent.CompletionStage

import scala.compat.java8.FutureConverters._

/**
 * API MAY CHANGE
 */
@ApiMayChange
object SourceWithContext {
  def from[Out, Mat](underlying: Source[Out, Mat]): SourceWithContext[Out, Out, Mat] = {
    new SourceWithContext(scaladsl.SourceWithContext(underlying.asScala))
  }

  def fromPairs[Out, Ctx, Mat](under: Source[Pair[Out, Ctx], Mat]): SourceWithContext[Ctx, Out, Mat] = {
    new SourceWithContext(scaladsl.SourceWithContext.from(under.asScala.map(_.toScala)))
  }
}

/**
 * API MAY CHANGE
 */
@ApiMayChange
final class SourceWithContext[+Ctx, +Out, +Mat](delegate: scaladsl.SourceWithContext[Ctx, Out, Mat]) extends GraphDelegate(delegate) {
  def mapContext[Ctx2](extractContext: function.Function[Ctx, Ctx2]): SourceWithContext[Ctx2, Out, Mat] = {
    new SourceWithContext(delegate.mapContext(extractContext.apply))
  }

  def via[Ctx2, Out2, Mat2](viaFlow: Graph[FlowShape[Pair[Out @uncheckedVariance, Ctx @uncheckedVariance], Pair[Out2, Ctx2]], Mat2]): SourceWithContext[Ctx2, Out2, Mat] = {
    val under = endContextPropagation().via(viaFlow)
    SourceWithContext.fromPairs(under)
  }

  def endContextPropagation(): Source[Pair[Out @uncheckedVariance, Ctx @uncheckedVariance], Mat @uncheckedVariance] =
    delegate.endContextPropagation.map { case (o, c) ⇒ Pair(o, c) }.asJava

  def map[Out2](f: function.Function[Out, Out2]): SourceWithContext[Ctx, Out2, Mat] =
    new SourceWithContext(delegate.map(f.apply))

  def mapAsync[Out2](parallelism: Int, f: function.Function[Out, CompletionStage[Out2]]): SourceWithContext[Ctx, Out2, Mat] =
    new SourceWithContext(delegate.mapAsync[Out2](parallelism)(o ⇒ f.apply(o).toScala))

  def collect[Out2](pf: PartialFunction[Out, Out2]): SourceWithContext[Ctx, Out2, Mat] =
    new SourceWithContext(delegate.collect(pf))

  def filter(p: function.Predicate[Out]): SourceWithContext[Ctx, Out, Mat] =
    new SourceWithContext(delegate.filter(p.test))

  def filterNot(p: function.Predicate[Out]): SourceWithContext[Ctx, Out, Mat] =
    new SourceWithContext(delegate.filterNot(p.test))

  def grouped(n: Int): SourceWithContext[java.util.List[Ctx @uncheckedVariance], java.util.List[Out @uncheckedVariance], Mat] =
    new SourceWithContext(delegate.grouped(n).map(_.asJava)).mapContext(_.asJava)

  def mapConcat[Out2](f: function.Function[Out, _ <: java.lang.Iterable[Out2]]): SourceWithContext[Ctx, Out2, Mat] =
    new SourceWithContext(delegate.mapConcat(elem ⇒ Util.immutableSeq(f.apply(elem))))

  def statefulMapConcat[Out2](f: function.Creator[function.Function[Out, java.lang.Iterable[Out2]]]): SourceWithContext[Ctx, Out2, Mat] =
    new SourceWithContext(delegate.statefulMapConcat { () ⇒
      val fun = f.create()
      elem ⇒ Util.immutableSeq(fun(elem))
    })

  def asScala: scaladsl.SourceWithContext[Ctx, Out, Mat] = delegate
}
