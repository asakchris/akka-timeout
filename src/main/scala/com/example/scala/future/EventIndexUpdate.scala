package com.example.scala.future

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.ActorMaterializer
import com.example.scala.messages.EventIndex
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

class EventIndexUpdate(implicit system: ActorSystem) {
  private val log = LoggerFactory.getLogger(getClass)

  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = materializer.executionContext

  def updateEventIndexProc(eventIndex: EventIndex, status: Int, errorMessage: Option[String]): Future[Int] = {
    log.info(s"Before calling updateEventIndex proc: ${eventIndex.eventId}, indexId: ${eventIndex.indexId}")
    Future { 1 }
  }

  def execute(eventIndex: EventIndex, status: Int, errorMessage: Option[String], eventIndexWorkerActor: ActorRef): Future[Unit] = {
    log.info(s"Before updateEventIndex status: ${eventIndex.eventId}, indexId: ${eventIndex.indexId}")
    // For Index Level category, update all event index since processing done in bulk
    val dbResult = Future { 1 }
    dbResult.map(rows => eventIndexWorkerActor.tell(EventIndexUpdateResponse(rows, eventIndex), eventIndexWorkerActor))
  }
}

case class EventIndexUpdateResponse(rows: Int, eventIndex: EventIndex)