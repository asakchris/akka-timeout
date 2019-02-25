package com.example.scala.messages

case class EventInit(status: String)

case class EventSummary(eventId: Long)

case class EventDetail(eventId: Long) {
  override def canEqual(that: Any): Boolean = that.isInstanceOf[EventDetail]

  override def equals(that: Any): Boolean =
    that match {
      case that: EventDetail => that.canEqual(this) && this.hashCode == that.hashCode
      case _ => false
    }

  override def hashCode(): Int = eventId.##
}

case class EventIndex(eventId: Long, indexId: Long) {
  override def canEqual(that: Any): Boolean = that.isInstanceOf[EventIndex]

  override def equals(that: Any): Boolean =
    that match {
      case that: EventIndex => that.canEqual(this) && this.hashCode == that.hashCode
      case _ => false
    }

  override def hashCode(): Int = {
    val prime = 31
    var result = 1
    result = prime * result + eventId.hashCode
    result = prime * result + indexId.hashCode
    result
  }
}