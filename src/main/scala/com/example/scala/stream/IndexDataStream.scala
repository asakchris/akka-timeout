package com.example.scala.stream

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import com.example.scala.messages.EventIndex
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext

class IndexDataStream(implicit system: ActorSystem) {
  private val log = LoggerFactory.getLogger(getClass)

  val decider: Supervision.Decider = {
    case _ => Supervision.stop
  }

  implicit val materializer = ActorMaterializer(ActorMaterializerSettings(system).withSupervisionStrategy(decider))

  // Need this for futures
  implicit val executionContext: ExecutionContext = materializer.executionContext

  def streamIndexData(eventIndex: EventIndex) ={
    Source(1 to 15).runForeach(id => log.info(s"Processing Event Index in IndexDataStream: ${eventIndex.eventId}, indexId: ${eventIndex.indexId}, id: ${id}"))
  }

  def runStreamIndexData(eventIndex: EventIndex, actor: ActorRef) = {
    streamIndexData(eventIndex).map(_ => actor.tell(IndexDataStreamResponse(eventIndex), actor))
  }
}

case class IndexDataStreamResponse(eventIndex: EventIndex)
case class EventIndexException(eventIndex: EventIndex, throwable: Throwable) extends Exception(throwable)