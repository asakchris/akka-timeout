package com.example.actors;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import com.example.common.AppConstants;
import com.example.messages.AreYouFree;
import com.example.messages.IAmFree;
import com.example.messages.SchedulerMessage;
import com.example.scala.future.EventIndexUpdate;
import com.example.scala.future.EventIndexUpdateResponse;
import com.example.scala.messages.EventIndex;
import com.example.scala.stream.IndexDataStream;
import com.example.scala.stream.IndexDataStreamResponse;
import scala.Option;

import java.time.Duration;
import java.util.*;

public class EventIndexWorkerActor extends AbstractLoggingActor {
    private boolean isEventIndexInProgress;
    // All in-progress event indices stored in this map and will be removed after streaming index data and event index status update
    private final Map<EventIndex, ActorRef> inProgressEventsMap;
    // All in-progress event indices stored in this list and will be removed after streaming index data
    private final List<EventIndex> inProgressEventIndices;
    private final Queue<ActorRef> pendingCoordinatorMessages;
    private final IndexDataStream indexDataStream;
    private final long timeout;

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(EventIndex.class, this::processEventIndex)
                .match(AreYouFree.class, this::respondToBroadcast)
                .match(IndexDataStreamResponse.class, this::processIndexDataStreamResponse)
                .match(EventIndexUpdateResponse.class, this::processEventIndexUpdateResponse)
                .match(SchedulerMessage.class, this::processPendingBroadcastMessages)
                .match(Timeout.class, this::processTimeoutMessage)
                .build();
    }

    public EventIndexWorkerActor(long timeout) {
        this.timeout = timeout;
        inProgressEventsMap = new HashMap<>();
        pendingCoordinatorMessages = new LinkedList<>();
        inProgressEventIndices = new ArrayList<>();
        context().system().scheduler().schedule(Duration.ofSeconds(10), Duration.ofSeconds(1), self(), SchedulerMessage.INSTANCE, context().system().dispatcher(), self());
        this.indexDataStream = new IndexDataStream(context().system());
    }

    private void processEventIndex(EventIndex eventIndex) {
        log().info("Event index worker received EventIndex message: {}, indexId: {}", eventIndex.eventId(), eventIndex.indexId());

        this.isEventIndexInProgress = true;

        // Store the sender who is event index actor to respond later after processing
        this.inProgressEventsMap.put(eventIndex, getSender());
        this.inProgressEventIndices.add(eventIndex);

        // Schedule timeout message to monitor this event index progress
        context().system().scheduler().scheduleOnce(Duration.ofSeconds(timeout), self(), new Timeout(eventIndex), context().system().dispatcher(), ActorRef.noSender());

        // Invoke IndexDataStream to send data to Kafka
        indexDataStream.runStreamIndexData(eventIndex, self());
    }

    private void processIndexDataStreamResponse(IndexDataStreamResponse streamResponse) {
        log().info("Event index worker received IndexDataStreamResponse message from IndexDataStream: {}, indexId: {}", streamResponse.eventIndex().eventId(), streamResponse.eventIndex().indexId());

        // Check if event index exists in in-progress list, if it doesn't then, it is already timed out, so we can ignore it
        boolean isEventIndexExist = this.inProgressEventIndices.remove(streamResponse.eventIndex());
        if(isEventIndexExist) {
            updateEventIndexStatus(streamResponse.eventIndex(), AppConstants.ProcessingStatus.SUCCESSFUL, null);
        } else {
            log().warning("Index data stream processing completed after timeout, so ignoring it: {}, indexId: {}", streamResponse.eventIndex().eventId(), streamResponse.eventIndex().indexId());
        }
    }

    private void updateEventIndexStatus(EventIndex eventIndex, int status, String errorMessage) {
        EventIndexUpdate eventIndexUpdate = new EventIndexUpdate(context().system());
        eventIndexUpdate.execute(eventIndex, status, Option.apply(errorMessage), self());
    }

    /**
     * This actor receives Timeout if IndexDataStream didn't send response message after configured timeout
     * It updates event index status as failure and proceed
     */
    private void processTimeoutMessage(Timeout timeout) {
        log().debug("Event index worker received Timeout message: {}, indexId: {}", timeout.getEventIndex().eventId(), timeout.getEventIndex().indexId());

        // Check if event index exists in in-progress list, if it doesn't then, it didn't timeout, which means it already received response from IndexDataStream, so we can ignore this message
        boolean isEventIndexExist = this.inProgressEventIndices.remove(timeout.getEventIndex());
        if(isEventIndexExist) {
            log().info("Index data stream processing timed out, so failing it: {}, indexId: {}", timeout.getEventIndex().eventId(), timeout.getEventIndex().indexId());
            updateEventIndexStatus(timeout.getEventIndex(), AppConstants.ProcessingStatus.FAILED, "Index data stream processing timed out");
        } else {
            log().debug("Index data stream processing completed before timeout, so ignoring it: {}, indexId: {}", timeout.getEventIndex().eventId(), timeout.getEventIndex().indexId());
        }
    }

    private void respondToBroadcast(AreYouFree free) {
        // Respond to broadcast only when event index is not in-progress
        if(!this.isEventIndexInProgress) {
            log().debug("EventIndexWorkerActor received AreYouFree message from: {}", sender().path().name());
            getSender().tell(IAmFree.getInstance(), getSelf());
        } else {
            this.pendingCoordinatorMessages.offer(sender());
        }
    }

    private void processPendingBroadcastMessages(SchedulerMessage schedulerMessage) {
        if(!this.isEventIndexInProgress) {
            final ActorRef eventIndexActorRef = this.pendingCoordinatorMessages.poll();
            if(eventIndexActorRef != null) {
                log().debug("Received SchedulerMessage and responding to broadcast message from: {}", eventIndexActorRef.path().name());
                eventIndexActorRef.tell(IAmFree.getInstance(), getSelf());
            }
        }
    }

    /**
     * This actor receives EventIndexUpdateResponse from EventIndexUpdate future after updating event detail status
     * It sends WorkerResponse to EventIndexActor since all processing done for this event index
     */
    private void processEventIndexUpdateResponse(EventIndexUpdateResponse response) {
        log().info("Received EventIndexUpdateResponse from EventIndexUpdate future, rows updated: {}, eventId: {}, indexId: {}",
                response.rows(), response.eventIndex().eventId(), response.eventIndex().indexId());

        EventIndex eventIndex = response.eventIndex();
        ActorRef eventIndexActor = this.inProgressEventsMap.get(eventIndex);

        this.inProgressEventsMap.remove(eventIndex);

        eventIndexActor.tell(new EventIndexActor.WorkerResponse(eventIndex), context().self());

        log().info("Event index worker processed EventIndex message: {}, indexId: {}", eventIndex.eventId(), eventIndex.indexId());

        if(this.inProgressEventsMap.isEmpty()) {
            this.isEventIndexInProgress = false;
        }
    }

    public static Props props(long timeout) {
        return Props.create(EventIndexWorkerActor.class, () -> new EventIndexWorkerActor(timeout));
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
