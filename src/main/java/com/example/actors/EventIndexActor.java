package com.example.actors;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.routing.Broadcast;
import com.example.common.AppConstants;
import com.example.messages.*;
import com.example.scala.future.EventIndexUpdate;
import com.example.scala.future.EventIndexUpdateResponse;
import com.example.scala.messages.EventIndex;
import scala.Option;

import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class EventIndexActor extends AbstractLoggingActor {
    private Integer count = 0;
    private final ActorRef eventIndexWorkerRouter;
    // In-progress event index list
    private final List<EventIndex> eventIndexList;
    private boolean isStreamCompleted;
    // Stream which sends message to event index actor
    private ActorRef stream;
    // Event detail worker actor who creates event index stream, when all indices are processed for this event, then response should be sent to this actor
    private ActorRef eventDetailWorkerActor;
    private EventIndex eventIndex;
    private final long timeout;

    public EventIndexActor(ActorRef eventIndexWorkerRouter, long timeout) {
        eventIndexList = new ArrayList<>();
        this.eventIndexWorkerRouter = eventIndexWorkerRouter;
        this.timeout = timeout;
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(StreamResponse.StreamInitialized.class, this::processStreamInitializedMessage)
                .match(EventIndex.class, this::processEventIndexMessage)
                .match(StreamResponse.StreamCompleted.class, this::processStreamCompletedMessage)
                .match(StreamResponse.StreamFailure.class, this::processStreamFailureMessage)
                .match(IAmFree.class, this::processBroadcastResponse)
                .match(WorkerResponse.class, this::processWorkerResponse)
                .match(Timeout.class, this::processTimeoutMessage)
                .match(EventIndexUpdateResponse.class, this::processEventIndexUpdateResponse)
                .build();
    }

    private void processStreamInitializedMessage(StreamResponse.StreamInitialized initialized) {
        log().info("EventIndexActor received StreamInitialized message");
        // Initialize the actor to respond later
        this.stream = sender();
        this.eventDetailWorkerActor = initialized.getActor();
        this.count = 0;

        // Mark stream as not completed
        isStreamCompleted = false;

        sender().tell(Ack.INSTANCE, self());
    }

    private void processEventIndexMessage(EventIndex eventIndex) {
        count++;
        log().info("Received EventIndex message: eventId - {}, indexId - {}, - count: {}", eventIndex.eventId(), eventIndex.indexId(), count);

        // Add event index to the list
        this.eventIndexList.add(eventIndex);
        this.eventIndex = eventIndex;

        // Broadcast message to router group to identify free event index worker
        this.eventIndexWorkerRouter.tell(new Broadcast(AreYouFree.getInstance()), getSelf());
    }

    private void processStreamCompletedMessage(StreamResponse.StreamCompleted completed) {
        log().info("EventIndexActor received StreamCompleted message: {} - count: {}", completed.getEventDetail().eventId(), count);
        this.isStreamCompleted = true;

        /**
         *  If there is no event index to process in high priority queue, then this stream will not contain any elements
         *  So send response back to the Event Detail Worker, when there are no elements in the stream
         */
        if(this.count == 0) {
            eventDetailWorkerActor.tell(new EventIndexComplete(completed.getEventDetail().eventId(), AppConstants.ProcessingStatus.SUCCESSFUL), self());
            stream.tell(Ack.INSTANCE, self());
        }
    }

    private void processStreamFailureMessage(StreamResponse.StreamFailure failure) {
        log().error("EventIndexActor received StreamFailure message - {}, cause: {}", failure.getEventDetail().eventId(), failure.getCause());

        // If any failures, send response back to event detail actor
        eventDetailWorkerActor.tell(new EventIndexComplete(failure.getEventDetail().eventId(), AppConstants.ProcessingStatus.FAILED, failure.getCause().getMessage()), getSelf());
        this.isStreamCompleted = true;
    }

    // This message will be received when any of the worker in routing group is free
    private void processBroadcastResponse(IAmFree free) {
        log().debug("EventIndexActor received IAmFree message from EventIndexWorkerActor: {}", sender().path().name());

        // Send event index message to the free worker if it is not processed by any other worker
        // Since it is broadcast message, multiple worker may send I am free message, but only one message to process
        if(this.eventIndex != null) {
            // Schedule timeout message to monitor this event index progress
            context().system().scheduler().scheduleOnce(Duration.ofSeconds(timeout), self(), new Timeout(eventIndex), context().system().dispatcher(), ActorRef.noSender());

            log().info("About to send event index to worker: {}, indexId: {}", eventIndex.eventId(), eventIndex.indexId());
            sender().tell(this.eventIndex, self());
            this.eventIndex = null;

            // Send acknowledgement message to event index stream so that it will send next message
            stream.tell(Ack.INSTANCE, self());
        }
    }

    private void processWorkerResponse(WorkerResponse workerResponse) {
        log().info("EventIndexActor received response from event index worker - {}, indexId: {}", workerResponse.getEventIndex().eventId(), workerResponse.getEventIndex().indexId());

        // Check if event index exists in in-progress list, if it doesn't then, it is already timed out, so we can ignore it
        boolean isEventIndexExist = eventIndexList.remove(workerResponse.getEventIndex());

        if(isEventIndexExist) {
            // Send event index complete message if stream is completed and all events are processed by workers
            if(isStreamCompleted && eventIndexList.isEmpty()) {
                log().info("All events for this event detail are processed by event index workers, so sending message to event detail worker actor: {}, count: {}", workerResponse.getEventIndex().eventId(), count);
                eventDetailWorkerActor.tell(new EventIndexComplete(workerResponse.getEventIndex().eventId(), AppConstants.ProcessingStatus.SUCCESSFUL), getSelf());
            }
        } else {
            log().warning("Event index worker processing completed after timeout, so ignoring it: {}, indexId: {}", workerResponse.getEventIndex().eventId(), workerResponse.getEventIndex().indexId());
        }
    }

    /**
     * This actor receives Timeout if EventIndexWorkerActor didn't send WorkerResponse message before configured timeout
     * It updates event index status as failure and proceed
     */
    private void processTimeoutMessage(Timeout timeout) {
        log().debug("Event index actor received Timeout message: {}, indexId: {}", timeout.getEventIndex().eventId(), timeout.getEventIndex().indexId());

        // Check if event index exists in in-progress list, if it doesn't then, it didn't timeout, which means it already received response from EventIndexWorkerActor, so we can ignore this message
        boolean isEventIndexExist = this.eventIndexList.remove(timeout.getEventIndex());
        if(isEventIndexExist) {
            log().info("Event index worker processing timed out, so failing it: {}, indexId: {}", timeout.getEventIndex().eventId(), timeout.getEventIndex().indexId());
            updateEventIndexStatus(timeout.getEventIndex(), AppConstants.ProcessingStatus.FAILED, "Event index worker processing timed out");
        } else {
            log().debug("Event index worker processing completed before timeout, so ignoring it: {}, indexId: {}", timeout.getEventIndex().eventId(), timeout.getEventIndex().indexId());
        }
    }

    private void updateEventIndexStatus(EventIndex eventIndex, int status, String errorMessage) {
        EventIndexUpdate eventIndexUpdate = new EventIndexUpdate(context().system());
        eventIndexUpdate.execute(eventIndex, status, Option.apply(errorMessage), self());
    }

    /**
     * This actor receives EventIndexUpdateResponse from EventIndexUpdate future after updating event detail status as timed out
     * It sends EventIndexComplete to EventDetailWorkerActor if all event indices are processed for given event detail
     */
    private void processEventIndexUpdateResponse(EventIndexUpdateResponse response) {
        log().info("Received EventIndexUpdateResponse from EventIndexUpdate future, rows updated: {}, eventId: {}, indexId: {}",
                response.rows(), response.eventIndex().eventId(), response.eventIndex().indexId());

        // Send event index complete message if stream is completed and all events are processed by workers
        if(isStreamCompleted && eventIndexList.isEmpty()) {
            log().info("All events for this event detail are processed with timeout by event index workers, so sending message to event detail worker actor: {}, count: {}", response.eventIndex().eventId(), count);
            eventDetailWorkerActor.tell(new EventIndexComplete(response.eventIndex().eventId(), AppConstants.ProcessingStatus.SUCCESSFUL), getSelf());
        }
    }

    public static Props props(ActorRef eventIndexWorkerRouter, long timeout) {
        return Props.create(EventIndexActor.class, () -> new EventIndexActor(eventIndexWorkerRouter, timeout));
    }

    public static class WorkerResponse implements Serializable {
        private final EventIndex eventIndex;

        public WorkerResponse(EventIndex eventIndex) {
            this.eventIndex = eventIndex;
        }

        public EventIndex getEventIndex() {
            return eventIndex;
        }
    }

    public static class Timeout {
        private final EventIndex eventIndex;

        public Timeout(EventIndex eventIndex) {
            this.eventIndex = eventIndex;
        }

        public EventIndex getEventIndex() {
            return eventIndex;
        }
    }
}
