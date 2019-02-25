package com.example.actors;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.routing.Broadcast;
import akka.routing.FromConfig;
import com.example.messages.Ack;
import com.example.messages.AreYouFree;
import com.example.messages.IAmFree;
import com.example.messages.StreamResponse;
import com.example.scala.messages.EventDetail;
import scala.Serializable;
import scala.concurrent.Promise;

import java.util.ArrayList;
import java.util.List;

public class EventDetailActor extends AbstractLoggingActor {
    private Integer count = 0;
    private Promise<Integer> promise = new scala.concurrent.impl.Promise.DefaultPromise<>();
    private ActorRef eventDetailWorker = getContext().actorOf(FromConfig.getInstance().props(), "event-detail-router");
    private List<Long> eventIdList = new ArrayList<>();
    // Event summary stream which sends message to event detail actor
    private ActorRef stream;
    private EventDetail eventDetail;
    private boolean isStreamCompleted;

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(StreamResponse.StreamInitialized.class, this::processStreamInitializedMessage)
                .match(EventDetail.class, this::processEventDetailMessage)
                .match(StreamResponse.StreamCompleted.class, this::processStreamCompletedMessage)
                .match(StreamResponse.StreamFailure.class, this::processStreamFailureMessage)
                .match(IAmFree.class, this::processBroadcastResponse)
                .match(WorkerResponse.class, this::processWorkerResponse)
                .build();
    }

    private void processStreamInitializedMessage(StreamResponse.StreamInitialized initialized) {
        log().info("EventDetailActor received StreamInitialized message");
        // Save the reference to the stream to respond later
        stream = sender();
        // Initialize the future
        this.promise = initialized.getPromise();
        count = 0;

        // Mark stream as not completed
        isStreamCompleted = false;
        sender().tell(Ack.INSTANCE, self());
    }

    private void processEventDetailMessage(EventDetail eventDetail) {
        count++;
        log().info("Received EventDetail message: {} - count: {}", eventDetail.eventId(), count);

        // Add event detail to the list
        eventIdList.add(eventDetail.eventId());
        this.eventDetail = eventDetail;

        // Broadcast message to router group to identify free event detail worker
        eventDetailWorker.tell(new Broadcast(AreYouFree.getInstance()), getSelf());
    }

    private void processStreamCompletedMessage(StreamResponse.StreamCompleted completed) {
        log().info("EventDetailActor received StreamCompleted message - count: {}", count);
        isStreamCompleted = true;
    }

    private void processStreamFailureMessage(StreamResponse.StreamFailure failure) {
        log().error("EventDetailActor received StreamFailure message - {}", failure.getCause());
    }

    // This message will be received when any of the worker in routing group is free
    private void processBroadcastResponse(IAmFree free) {
        log().info("EventDetailActor received IAmFree message from EventDetailWorkerActor: {}", eventIdList);

        // Send event detail message to the free worker if it is not processed by any other worker
        // Since it is broadcast message, multiple worker may send I am free message, but only one message to process
        if(this.eventDetail != null) {
            log().info("About to send event detail to worker: {}", eventDetail.eventId());
            sender().tell(eventDetail, self());
            this.eventDetail = null;

            // Send acknowledgement message to sender stream so that stream will send next message
            stream.tell(Ack.INSTANCE, self());
        }
    }

    private void processWorkerResponse(WorkerResponse workerResponse) {
        log().info("EventDetailActor received response from worker - {}", workerResponse.getEventId());

        // Remove event from the list
        eventIdList.remove(workerResponse.getEventId());

        // Broadcast message to router group to identify free event detail worker
        eventDetailWorker.tell(new Broadcast(AreYouFree.getInstance()), getSelf());

        // Complete the future if stream is completed and all events are processed by workers
        if(isStreamCompleted && eventIdList.isEmpty()) {
            log().info("All events are processed by event detail workers, so about to complete future: {}", count);
            this.promise.success(count);
        }
    }

    public static Props props() {
        return Props.create(EventDetailActor.class, () -> new EventDetailActor());
    }

    public static class WorkerResponse implements Serializable {
        private final Long eventId;

        public WorkerResponse(Long eventId) {
            this.eventId = eventId;
        }

        public Long getEventId() {
            return eventId;
        }
    }
}
