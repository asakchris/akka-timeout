package com.example.scala.future

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.ActorMaterializer
import com.example.scala.messages.EventDetail
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

class EventDetailUpdate(implicit system: ActorSystem) {
  private val log = LoggerFactory.getLogger(getClass)

  implicit val materializer = ActorMaterializer()

  implicit val executionContext: ExecutionContext = materializer.executionContext

  def updateEvent(eventDetail: EventDetail, status: Int, errorMessage: String) = {
    log.info(s"Before calling updateEventDetailStatus proc: ${eventDetail.eventId}")
    Future { 1 }
  }

  def execute(eventDetail: EventDetail, status: Int, errorMessage: String, eventDetailWorkerActor: ActorRef) = {
    updateEvent(eventDetail, status, errorMessage)
      .map(rows => eventDetailWorkerActor.tell(EventDetailUpdateResponse(rows), eventDetailWorkerActor))
  }
}

case class EventDetailUpdateResponse(rows: Int)