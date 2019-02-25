package com.example.scala.stream

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, Cancellable}
import akka.stream.scaladsl.{RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import com.example.messages.Ack
import com.example.messages.StreamResponse.{StreamCompleted, StreamFailure, StreamInitialized}
import com.example.scala.messages.EventDetail
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}

class EventSummaryStream(implicit system: ActorSystem, eventDetailActor: ActorRef) {
  private val log = LoggerFactory.getLogger(getClass)

  val decider: Supervision.Decider = {
    case _ => Supervision.stop
  }

  implicit val materializer = ActorMaterializer(ActorMaterializerSettings(system).withSupervisionStrategy(decider))

  // Need this for futures
  implicit val executionContext: ExecutionContext = materializer.executionContext

  def eventSummaryFlow(): Future[Integer] = {
    val promise = Promise[Integer]()
    log.info("**************************** About to send Event Summary for processing ****************************************")
    Source(1 to 20)
      .map(id => EventDetail(id))
      .runWith(Sink.actorRefWithAck(eventDetailActor, onInitMessage = new StreamInitialized(promise), ackMessage = Ack.INSTANCE,
        onCompleteMessage = new StreamCompleted(), onFailureMessage = (ex: Throwable) => new StreamFailure(ex)))
    promise.future
  }

  def promiseTimedTick(): RunnableGraph[Cancellable] = {
    Source
      .tick(0 seconds, 5 seconds, NotUsed)
      .mapAsync(1)(_ => eventSummaryFlow())
      .to(Sink.ignore)
  }

  def processEvents(): Cancellable = {
    promiseTimedTick().run()
  }
}
