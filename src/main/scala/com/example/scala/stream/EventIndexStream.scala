package com.example.scala.stream

import akka.Done
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import com.example.messages.Ack
import com.example.messages.StreamResponse.{StreamCompleted, StreamFailure, StreamInitialized}
import com.example.scala.messages.{EventDetail, EventIndex}
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

class EventIndexStream(implicit system: ActorSystem, eventIndexActor: ActorRef, eventDetailWorkerActor: ActorRef) {
  private val log = LoggerFactory.getLogger(getClass)

  val decider: Supervision.Decider = {
    case _ => Supervision.stop
  }

  implicit val materializer = ActorMaterializer(ActorMaterializerSettings(system).withSupervisionStrategy(decider))
  implicit val executionContext: ExecutionContext = materializer.executionContext

  def streamEventIndex(eventDetail: EventDetail) = {
    Source(1 to 5)
      .map(id => EventIndex(eventDetail.eventId, id))
      .runWith(Sink.actorRefWithAck(eventIndexActor, onInitMessage = new StreamInitialized(eventDetailWorkerActor), ackMessage = Ack.INSTANCE,
        onCompleteMessage = new StreamCompleted(eventDetail), onFailureMessage = (ex: Throwable) => new StreamFailure(ex, eventDetail)))
  }

  def runLowPriorityEventIndex(eventDetail: EventDetail) = {
    val result = Future {Done.done()}
    result.map(_ => eventDetailWorkerActor.tell(EventIndexLowPriorityQueueResponse(), eventDetailWorkerActor))
  }
}

case class EventIndexLowPriorityQueueResponse()