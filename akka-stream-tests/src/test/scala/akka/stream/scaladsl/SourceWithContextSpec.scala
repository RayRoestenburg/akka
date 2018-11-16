/*
 * Copyright (C) 2014-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.stream.scaladsl

import akka.stream.ActorAttributes._
import akka.stream.Supervision._
import akka.stream.testkit.Utils.TE
import akka.stream.testkit.scaladsl.TestSink

import java.util.concurrent.ThreadLocalRandom.{ current ⇒ random }

import akka.stream.{ ActorMaterializer, ActorMaterializerSettings }
import akka.stream.testkit.{ StreamSpec, ScriptedTest }

case class Message(data: String, offset: Long)

class SourceWithContextSpec extends StreamSpec {

  val settings = ActorMaterializerSettings(system)
  implicit val materializer = ActorMaterializer(settings)

  "A SourceWithContext" must {

    "provide a one-to-one context when map is used" in {
      Source(Vector(Message("a", 1L)))
        .withContext
        .map(_.data)
        .mapContext(_.offset)
        .map(_.toLowerCase)
        .provideContext
        .runWith(TestSink.probe[(String, Op[Long])])
        .request(1)
        .expectNext(("a", OneToOne(1L)))
        .expectComplete()

    }

    "provide a one-to-one context via a FlowWithContext" in {

      def flowWithContext[T] = FlowWithContext[Long, T]

      Source(Vector(Message("a", 1L)))
        .withContext
        .map(_.data)
        .mapContext(_.offset)
        .via(flowWithContext.map { s ⇒
          s + "b"
        })
        .provideContext
        .runWith(TestSink.probe[(String, Op[Long])])
        .request(1)
        .expectNext(("ab", OneToOne(1L)))
        .expectComplete()
    }

    "provide a one-to-many context via mapConcat" in {
      Source(Vector(Message("a", 1L)))
        .withContext
        .map(_.data)
        .mapContext(_.offset)
        .mapConcat { str ⇒
          List(1, 2, 3).map(i ⇒ s"$str-$i")
        }
        .provideContext
        .runWith(TestSink.probe[(String, Op[Long])])
        .request(3)
        .expectNext(("a-1", OneToMany(1L)), ("a-2", OneToMany(1L)), ("a-3", OneToMany(1L)))
        .expectComplete()
    }
  }
}
