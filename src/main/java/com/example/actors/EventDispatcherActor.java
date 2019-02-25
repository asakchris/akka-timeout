package com.example.actors;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import com.example.messages.Ack;
import com.example.messages.StreamResponse;
import com.example.scala.messages.EventInit;
import com.example.scala.stream.EventInitStream;
import com.example.scala.stream.EventSummaryStream;

public class EventDispatcherActor extends AbstractLoggingActor {
    private final ActorRef eventDetailActor;
    private EventInitStream eventInitStream;
    private EventSummaryStream eventSummaryStream;
    private final Cluster cluster = Cluster.get(context().system());

    public EventDispatcherActor(ActorRef eventDetailActor) {
        this.eventDetailActor = eventDetailActor;
    }

    @Override
    public void preStart() {
        cluster.subscribe(self(), ClusterEvent.MemberUp.class);

        eventInitStream = new EventInitStream(context().system(), self());
        eventSummaryStream = new EventSummaryStream(context().system(), eventDetailActor);

        // This is the starting point. When this actor starts, it sends Init message to itself
        self().tell(new Init(), self());
    }

    @Override
    public void postStop() {
        cluster.unsubscribe(self());
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Init.class, this::processInitMessage)
                .match(StreamResponse.StreamInitialized.class, this::processStreamInitializedMessage)
                .match(EventInit.class, this::processEventInitMessage)
                .match(StreamResponse.StreamCompleted.class, this::processStreamCompletedMessage)
                .match(StreamResponse.StreamFailure.class, this::processStreamFailureMessage)
                .build();
    }

    private void processInitMessage(Init init) {
        log().info("EventDispatcherActor received Init message");
        eventInitStream.streamEventInit();
    }

    private void processStreamInitializedMessage(StreamResponse.StreamInitialized initialized) {
        log().info("EventDispatcherActor received StreamInitialized message");
        sender().tell(Ack.INSTANCE, self());
    }

    private void processStreamCompletedMessage(StreamResponse.StreamCompleted completed) {
        log().info("EventDispatcherActor received StreamCompleted message");
    }

    private void processStreamFailureMessage(StreamResponse.StreamFailure failure) {
        log().error("EventDispatcherActor received StreamFailure message - {}", failure.getCause());
    }

    private void processEventInitMessage(EventInit eventInit) {
        log().info("EventDispatcherActor received EventInit message - {}", eventInit.status());
        eventSummaryStream.processEvents();
        sender().tell(Ack.INSTANCE, self());
    }

    public static Props props(ActorRef eventDetailActor) {
        return Props.create(EventDispatcherActor.class, () -> new EventDispatcherActor(eventDetailActor));
    }

    public static class Init {}
}
