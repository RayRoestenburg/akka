/*
 * Copyright (C) 2014-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.stream.scaladsl

import scala.collection.immutable
import scala.concurrent.Future
import scala.language.higherKinds
import scala.annotation.unchecked.uncheckedVariance

import akka.dispatch.ExecutionContexts
import akka.stream._
import akka.stream.impl.LinearTraversalBuilder

/**
 * Describes the usage of a stream operator, passing through a context of type `Ctx`.
 * The context is passed from input elements to output elements.
 */
sealed trait Op[+Ctx] {
  def map[Ctx2](f: Ctx ⇒ Ctx2): Op[Ctx2]
}

/**
 * Describes a one-to-one type operator, which passes the `context`
 * from one input element to one output element.
 */
case class OneToOne[+Ctx](context: Ctx) extends Op[Ctx] {
  def map[Ctx2](f: Ctx ⇒ Ctx2): Op[Ctx2] = OneToOne(f(context))
}

/**
 * Describes a one-to-many type operator, which passes the `context`
 * from one input element to many output elements.
 */
case class OneToMany[+Ctx](context: Ctx, last: Boolean = false) extends Op[Ctx] {
  def map[Ctx2](f: Ctx ⇒ Ctx2): Op[Ctx2] = OneToMany(f(context))
}

/**
 * Describes a many-to-one type operator, which passes the contexts
 * from many input elements to one output element.
 */
case class ManyToOne[+Ctx](contexts: Seq[Ctx]) extends Op[Ctx] {
  def map[Ctx2](f: Ctx ⇒ Ctx2): Op[Ctx2] = ManyToOne(contexts.map(f))
}

/**
 * The contexts of many input elements is passed on to many output elements.
 */
case class ManyToMany[+Ctx](contexts: Seq[Ctx]) extends Op[Ctx] {
  def map[Ctx2](f: Ctx ⇒ Ctx2): Op[Ctx2] = ManyToMany(contexts.map(f))
}

object Op {
  /**
   * Applies a many-to-many operation.
   */
  private[akka] def manyToMany[Ctx](contexts: Seq[Op[Ctx]]): ManyToMany[Ctx] = ManyToMany(contexts.flatMap {
    case OneToOne(ctx)     ⇒ Seq(ctx)
    case OneToMany(ctx, _) ⇒ Seq(ctx)
    case ManyToOne(ctxs)   ⇒ ctxs
    case ManyToMany(ctxs)  ⇒ ctxs
  })

  /**
   * Applies a one-to-many operation.
   */
  private[akka] def oneToMany[Ctx](op: Op[Ctx]) = op match {
    case OneToOne(ctx)        ⇒ OneToMany(ctx)
    case otm: OneToMany[Ctx]  ⇒ otm
    case ManyToOne(ctxs)      ⇒ ManyToMany(ctxs)
    case mtm: ManyToMany[Ctx] ⇒ mtm
  }
}

// TODO viaMat, FlowWithContextOpsMat
trait FlowWithContextOps[+Ctx, +Out, +Mat] {
  import akka.stream.impl.Stages._
  import GraphDSL.Implicits._

  type Repr[+C, +O] <: FlowWithContextOps[C, O, Mat] {
    type Repr[+CC, +OO] = FlowWithContextOps.this.Repr[CC, OO]
    type Prov[+CC, +OO] = FlowWithContextOps.this.Prov[CC, OO]
  }

  type Prov[+C, +O] <: FlowOpsMat[(O, Op[C]), Mat]

  def via[Ctx2, Out2, Mat2](flow: Graph[FlowShape[(Out, Op[Ctx]), (Out2, Op[Ctx2])], Mat2]): Repr[Ctx2, Out2]

  def map[Out2](f: Out ⇒ Out2): Repr[Ctx, Out2]
  def mapAsync[Out2](parallelism: Int)(f: Out ⇒ Future[Out2]): Repr[Ctx, Out2]
  def collect[Out2](f: PartialFunction[Out, Out2]): Repr[Ctx, Out2]
  def filter(pred: Out ⇒ Boolean): Repr[Ctx, Out]

  def mapConcat[Out2](f: Out ⇒ immutable.Iterable[Out2]): Repr[Ctx, Out2]
  def statefulMapConcat[Out2](f: () ⇒ Out ⇒ immutable.Iterable[Out2]): Repr[Ctx, Out2]
  def grouped(n: Int): Repr[Ctx, immutable.Seq[Out]]

  def mapContext[Ctx2](f: Ctx ⇒ Ctx2): Repr[Ctx2, Out]

  def provideContext: Prov[Ctx, Out]
}

object FlowWithContext {
  def apply[Out, Mat](underlying: Flow[Out, Out, Mat]): FlowWithContextOps[Out, Out, Mat] = {
    val under =
      Flow[(Out, Op[Out])].map {
        case (e, _) ⇒ e
      }.viaMat(underlying.map(e ⇒ (e, OneToOne(e): Op[Out])))(Keep.right)
    new FlowWithContext[Out, Out, Out, Out, Mat](under, under.traversalBuilder, under.shape)
  }

  def apply[Ctx, In]: FlowWithContext[Ctx, In, Ctx, In, akka.NotUsed] = {
    val under = Flow[(In, Op[Ctx])]
    new FlowWithContext[Ctx, In, Ctx, In, akka.NotUsed](under, under.traversalBuilder, under.shape)
  }
}

final class FlowWithContext[-CtxIn, -In, +CtxOut, +Out, +Mat](
  underlying:                    Flow[(In, Op[CtxIn]), (Out, Op[CtxOut]), Mat],
  override val traversalBuilder: LinearTraversalBuilder,
  override val shape:            FlowShape[(In, Op[CtxIn]), (Out, Op[CtxOut])]
) extends FlowWithContextOps[CtxOut, Out, Mat] with Graph[FlowShape[(In, Op[CtxIn]), (Out, Op[CtxOut])], Mat] {

  override def withAttributes(attr: Attributes): Repr[CtxOut, Out] =
    new FlowWithContext(underlying, traversalBuilder.setAttributes(attr), shape)

  override type Repr[+C, +O] = FlowWithContext[CtxIn @uncheckedVariance, In @uncheckedVariance, C, O, Mat @uncheckedVariance]

  override type Prov[+C, +O] = Flow[(In @uncheckedVariance, Op[CtxIn @uncheckedVariance]), (O, Op[C]), Mat @uncheckedVariance]

  override def via[Ctx2, Out2, Mat2](flow: Graph[FlowShape[(Out, Op[CtxOut]), (Out2, Op[Ctx2])], Mat2]): Repr[Ctx2, Out2] = {
    from(underlying.via(flow))
  }

  def map[Out2](f: Out ⇒ Out2): Repr[CtxOut, Out2] =
    from(underlying.map { case (out, ctx) ⇒ (f(out), ctx) })

  def mapAsync[Out2](parallelism: Int)(f: Out ⇒ Future[Out2]): Repr[CtxOut, Out2] =
    from(underlying.mapAsync(parallelism) { case (e, ctx) ⇒ f(e).map(o ⇒ (o, ctx))(ExecutionContexts.sameThreadExecutionContext) })

  def collect[Out2](f: PartialFunction[Out, Out2]): Repr[CtxOut, Out2] =
    from(underlying.collect {
      case (e, ctx) if f.isDefinedAt(e) ⇒ (f(e), ctx)
    })

  def filter(pred: Out ⇒ Boolean): Repr[CtxOut, Out] =
    collect { case e if pred(e) ⇒ e }

  def grouped(n: Int): Repr[CtxOut, immutable.Seq[Out]] =
    from(underlying.grouped(n).map { elsWithContext ⇒
      val (els, ctxs) = elsWithContext.unzip
      (els, Op.manyToMany(ctxs))
    })

  def mapConcat[Out2](f: Out ⇒ immutable.Iterable[Out2]): Repr[CtxOut, Out2] = statefulMapConcat(() ⇒ f)

  def statefulMapConcat[Out2](f: () ⇒ Out ⇒ immutable.Iterable[Out2]): Repr[CtxOut, Out2] = {
    val fCtx: () ⇒ ((Out, Op[CtxOut])) ⇒ immutable.Iterable[(Out2, Op[CtxOut])] = { () ⇒ elWithContext ⇒
      val (el, ctx) = elWithContext
      val newCtx = Op.oneToMany(ctx)
      f()(el).map(o ⇒ (o, newCtx))
    }
    from(underlying.statefulMapConcat(fCtx))
  }

  def mapContext[CtxOut2](f: CtxOut ⇒ CtxOut2): Repr[CtxOut2, Out] =
    from(underlying.map { case (e, ctx) ⇒ (e, ctx.map(f)) })

  override def provideContext: Prov[CtxOut, Out] = underlying

  private[this] def from[CI, I, CO, O, M](flow: Flow[(I, Op[CI]), (O, Op[CO]), M]) = {
    new FlowWithContext(flow, flow.traversalBuilder, flow.shape)
  }
}

