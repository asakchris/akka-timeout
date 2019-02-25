package com.example.actors;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import com.example.messages.AreYouFree;
import com.example.messages.EventIndexComplete;
import com.example.messages.IAmFree;
import com.example.scala.future.EventDetailUpdate;
import com.example.scala.future.EventDetailUpdateResponse;
import com.example.scala.messages.EventDetail;
import com.example.scala.stream.EventIndexLowPriorityQueueResponse;
import com.example.scala.stream.EventIndexStream;

public class EventDetailWorkerActor extends AbstractLoggingActor {
    // Reference to event detail coordinator which sends message to this actor
    private ActorRef eventDetailActor;
    private final ActorRef eventIndexActor;
    private boolean isEventDetailInProgress;
    private EventDetail eventDetail;
    private final EventIndexStream eventIndexStream;

    public EventDetailWorkerActor(ActorRef eventIndexWorkerRouter) {
        eventIndexActor = getContext().actorOf(EventIndexActor.props(eventIndexWorkerRouter), "event-index-coordinator-" + self().path().name());
        eventIndexStream = new EventIndexStream(context().system(), eventIndexActor, getSelf());
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(EventDetail.class, this::processEventDetail)
                .match(EventIndexComplete.class, this::eventIndexCompleteMessage)
                .match(AreYouFree.class, this::respondToBroadcast)
                .match(EventDetailUpdateResponse.class, this::processEventDetailUpdateResponse)
                .match(EventIndexLowPriorityQueueResponse.class, this::processEventIndexLowPriorityQueueResponse)
                .build();
    }

    /**
     * When this actor receives EventDetail message, it creates EventIndexStream which sends EventIndex message to
     * Event Index Coordinator actor. Then coordinator distributes the work to its workers.
     * When all messages are processed Event Index Coordinator sends EventIndexComplete message to this actor
     */
    private void processEventDetail(EventDetail eventDetail) {
        log().info("Event detail worker received EventDetail message: {}", eventDetail.eventId());

        this.eventDetailActor = getSender();
        this.isEventDetailInProgress = true;
        this.eventDetail = eventDetail;

        eventIndexStream.streamEventIndex(eventDetail);
    }

    /**
     * This actor receives EventIndexComplete from Event Index Coordinator actor when it processes all event index
     * messages for this event detail. It invokes EventDetailUpdate future to update event detail status
     */
    private void eventIndexCompleteMessage(EventIndexComplete eventIndexComplete) {
        log().info("Received EventIndexComplete from EventIndexActor: {}", eventIndexComplete.getEventId());

        EventDetailUpdate eventDetailUpdate = new EventDetailUpdate(context().system());
        eventDetailUpdate.execute(eventDetail, eventIndexComplete.getStatus(), eventIndexComplete.getErrorMessage(), self());
    }

    private void respondToBroadcast(AreYouFree free) {
        // Respond to broadcast only when event detail is not in-progress
        if(!this.isEventDetailInProgress) {
            log().debug("EventIndexWorkerActor received AreYouFree message");
            getSender().tell(IAmFree.getInstance(), getSelf());
        }
    }

    /**
     * This actor receives EventDetailUpdateResponse from EventDetailUpdate future after updating event detail status
     * It calls EventIndexStream to send low priority event index of constituent category into kafka topic
     */
    private void processEventDetailUpdateResponse(EventDetailUpdateResponse response) {
        log().info("Received EventDetailUpdateResponse from EventDetailUpdate future: {}, event summary rows updated: {}", eventDetail.eventId(), response.rows());
        eventIndexStream.runLowPriorityEventIndex(eventDetail);
    }

    /**
     * This actor receives EventIndexLowPriorityQueueResponse from EventIndexStream after sending low priority
     * event index of constituent category into kafka topic
     * It sends WorkerResponse to eventDetailActor since all processing done for this event detail
     */
    private void processEventIndexLowPriorityQueueResponse(EventIndexLowPriorityQueueResponse response) {
        log().info("Received EventIndexLowPriorityQueueResponse from EventIndexStream: {}", eventDetail.eventId());
        eventDetailActor.tell(new EventDetailActor.WorkerResponse(eventDetail.eventId()), context().self());
        log().info("Event detail worker processed EventDetail message: {}", eventDetail.eventId());

        this.isEventDetailInProgress = false;
        this.eventDetail = null;
    }

    public static Props props(ActorRef eventIndexWorkerRouter) {
        return Props.create(EventDetailWorkerActor.class, () -> new EventDetailWorkerActor(eventIndexWorkerRouter));
    }
}
