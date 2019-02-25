package com.example.scala.stream

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import com.example.messages.Ack
import com.example.messages.StreamResponse.{StreamCompleted, StreamFailure, StreamInitialized}
import com.example.scala.messages.EventInit

class EventInitStream(implicit system: ActorSystem, eventDispatcherActor: ActorRef) {
  val decider: Supervision.Decider = {
    case _ => Supervision.stop
  }

  implicit val materializer = ActorMaterializer(ActorMaterializerSettings(system).withSupervisionStrategy(decider))

  def eventDispatcherActorWithAckSink = {
    val initMessage = new StreamInitialized()
    val ackMessage = Ack.INSTANCE
    val onCompleteMessage = new StreamCompleted()
    val onErrorMessage = (ex: Throwable) => new StreamFailure(ex)
    Sink.actorRefWithAck(eventDispatcherActor, onInitMessage = initMessage, ackMessage = ackMessage, onCompleteMessage = onCompleteMessage, onFailureMessage = onErrorMessage)
  }

  def streamEventInit = {
    Source(1 to 1).map(_ => EventInit("SUCCESS")).runWith(eventDispatcherActorWithAckSink)
  }
}
