package com.example.scala.stream

import akka.Done
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import com.example.scala.messages.EventIndex
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

class IndexDataStream(implicit system: ActorSystem) {
  private val log = LoggerFactory.getLogger(getClass)

  val decider: Supervision.Decider = {
    case _ => Supervision.stop
  }

  implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(system).withSupervisionStrategy(decider))

  // Need this for futures
  implicit val executionContext: ExecutionContext = materializer.executionContext

  def streamIndexData(eventIndex: EventIndex): Future[Done] = for {
    _ <- {
      if(eventIndex.eventId % 9 == 0 && eventIndex.indexId == 1) Thread.sleep(30000)
      Future[Int](0)
    }
    processor <- Source(1 to 15).runForeach(id => log.info(s"Processing Event Index in IndexDataStream: ${eventIndex.eventId}, indexId: ${eventIndex.indexId}, id: ${id}"))
  } yield processor

  def runStreamIndexData(eventIndex: EventIndex, actor: ActorRef) = {
    streamIndexData(eventIndex).map(_ => actor.tell(IndexDataStreamResponse(eventIndex), actor))
  }
}

case class IndexDataStreamResponse(eventIndex: EventIndex)
case class EventIndexException(eventIndex: EventIndex, throwable: Throwable) extends Exception(throwable)