/*
 * Copyright (C) 2014-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.stream.scaladsl

import scala.collection.immutable
import scala.concurrent.Future
import scala.annotation.unchecked.uncheckedVariance

import akka.NotUsed
import akka.dispatch.ExecutionContexts
import akka.stream._
import akka.stream.impl.LinearTraversalBuilder

object SourceWithContext {
  def apply[Out, Mat](underlying: Source[Out, Mat]): SourceWithContext[Out, Out, Mat] = {
    val under = underlying.map(e ⇒ (e, OneToOne(e)))
    new SourceWithContext[Out, Out, Mat](under, under.traversalBuilder, under.shape)
  }
}

final class SourceWithContext[+Ctx, +Out, +Mat](
  underlying:                    Source[(Out, Op[Ctx]), Mat],
  override val traversalBuilder: LinearTraversalBuilder,
  override val shape:            SourceShape[(Out, Op[Ctx])]
) extends FlowWithContextOps[Ctx, Out, Mat] with Graph[SourceShape[(Out, Op[Ctx])], Mat] {

  override def withAttributes(attr: Attributes): Repr[Ctx, Out] =
    new SourceWithContext(underlying, traversalBuilder.setAttributes(attr), shape)

  override type Repr[+C, +O] = SourceWithContext[C, O, Mat @uncheckedVariance]
  override type Prov[+C, +O] = Source[(O, Op[C]), Mat @uncheckedVariance]

  override def via[Ctx2, Out2, Mat2](viaFlow: Graph[FlowShape[(Out, Op[Ctx]), (Out2, Op[Ctx2])], Mat2]): Repr[Ctx2, Out2] = {
    from(flow.via(viaFlow))
  }

  override def map[Out2](f: Out ⇒ Out2): Repr[Ctx, Out2] =
    from(flow.map { case (e, ctx) ⇒ (f(e), ctx) })

  override def mapAsync[Out2](parallelism: Int)(f: Out ⇒ Future[Out2]): Repr[Ctx, Out2] =
    from(flow.mapAsync(parallelism) { case (e, ctx) ⇒ f(e).map(o ⇒ (o, ctx))(ExecutionContexts.sameThreadExecutionContext) })

  override def collect[Out2](f: PartialFunction[Out, Out2]): Repr[Ctx, Out2] =
    from(flow.collect {
      case (e, ctx) if f.isDefinedAt(e) ⇒ (f(e), ctx)
    })

  override def filter(pred: Out ⇒ Boolean): Repr[Ctx, Out] =
    collect { case e if pred(e) ⇒ e }

  override def grouped(n: Int): Repr[Ctx, immutable.Seq[Out]] =
    from(flow.grouped(n).map { elsWithContext ⇒
      val (els, ctxs) = elsWithContext.unzip
      (els, Op.manyToMany(ctxs))
    })

  def mapConcat[Out2](f: Out ⇒ immutable.Iterable[Out2]): Repr[Ctx, Out2] = statefulMapConcat(() ⇒ f)

  def statefulMapConcat[Out2](f: () ⇒ Out ⇒ immutable.Iterable[Out2]): Repr[Ctx, Out2] = {
    val fCtx: () ⇒ ((Out, Op[Ctx])) ⇒ immutable.Iterable[(Out2, Op[Ctx])] = { () ⇒ elWithContext ⇒
      val (el, ctx) = elWithContext
      val newCtx = Op.oneToMany(ctx)
      f()(el).map(o ⇒ (o, newCtx))
    }
    from(flow.statefulMapConcat(fCtx))
  }

  override def mapContext[Ctx2](f: Ctx ⇒ Ctx2): Repr[Ctx2, Out] =
    from(flow.map { case (e, ctx) ⇒ (e, ctx.map(f)) })

  override def provideContext: Prov[Ctx, Out] = underlying

  private[this] def from[Out2, Ctx2](f: Flow[(Out, Op[Ctx]), (Out2, Op[Ctx2]), Any]): Repr[Ctx2, Out2] = {
    val under = underlying.via(f)
    new SourceWithContext[Ctx2, Out2, Mat](under, under.traversalBuilder, under.shape)
  }

  private[this] def flow: Flow[(Out, Op[Ctx]), (Out, Op[Ctx]), NotUsed] = Flow[(Out, Op[Ctx])]
}

