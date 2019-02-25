package com.example.actors;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import com.example.messages.AreYouFree;
import com.example.messages.IAmFree;
import com.example.messages.SchedulerMessage;
import com.example.scala.future.EventIndexUpdate;
import com.example.scala.future.EventIndexUpdateResponse;
import com.example.scala.messages.EventIndex;
import com.example.scala.stream.IndexDataStream;
import com.example.scala.stream.IndexDataStreamResponse;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

public class EventIndexWorkerActor extends AbstractLoggingActor {
    private boolean isEventIndexInProgress;
    private final Map<EventIndex, ActorRef> inProgressEventsMap;
    private final Queue<ActorRef> pendingCoordinatorMessages;
    private final IndexDataStream indexDataStream;

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(EventIndex.class, this::processEventIndex)
                .match(AreYouFree.class, this::respondToBroadcast)
                .match(IndexDataStreamResponse.class, this::processIndexDataStreamResponse)
                .match(EventIndexUpdateResponse.class, this::processEventIndexUpdateResponse)
                .match(SchedulerMessage.class, this::processPendingBroadcastMessages)
                .build();
    }

    public EventIndexWorkerActor() {
        inProgressEventsMap = new HashMap<>();
        pendingCoordinatorMessages = new LinkedList<>();
        context().system().scheduler().schedule(Duration.ofSeconds(10), Duration.ofSeconds(1), self(), SchedulerMessage.INSTANCE, context().system().dispatcher(), self());
        this.indexDataStream = new IndexDataStream(context().system());
    }

    private void processEventIndex(EventIndex eventIndex) {
        log().info("Event index worker received EventIndex message: {}, indexId: {}", eventIndex.eventId(), eventIndex.indexId());

        this.isEventIndexInProgress = true;

        // Store the sender who is event index actor to respond later after processing
        this.inProgressEventsMap.put(eventIndex, getSender());

        // Invoke IndexDataStream to send data to Kafka
        indexDataStream.runStreamIndexData(eventIndex, self());
    }

    private void processIndexDataStreamResponse(IndexDataStreamResponse streamResponse) {
        log().info("Event index worker received IndexDataStreamResponse message from IndexDataStream: {}, indexId: {}", streamResponse.eventIndex().eventId(), streamResponse.eventIndex().indexId());

        // Update event index status
        EventIndexUpdate eventIndexUpdate = new EventIndexUpdate(context().system());
        eventIndexUpdate.execute(streamResponse.eventIndex(), 8, self());
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

    public static Props props() {
        return Props.create(EventIndexWorkerActor.class, () -> new EventIndexWorkerActor());
    }
}
