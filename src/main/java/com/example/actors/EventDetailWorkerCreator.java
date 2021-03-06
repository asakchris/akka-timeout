package com.example.actors;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;

import java.util.stream.IntStream;

public class EventDetailWorkerCreator extends AbstractLoggingActor {
    private final ActorRef eventIndexWorkerRouter;
    private final int noOfWorkers;

    public EventDetailWorkerCreator(ActorRef eventIndexWorkerRouter, int noOfWorkers) {
        this.eventIndexWorkerRouter = eventIndexWorkerRouter;
        this.noOfWorkers = noOfWorkers;
    }

    @Override
    public void preStart() {
        long eventIndexTimeout = context().system().settings().config().getLong("event.timeout-sec.event-index-coordinator");
        log().info("event-index-coordinator actor timeout: {}", eventIndexTimeout);

        IntStream
                .rangeClosed(1, noOfWorkers)
                .forEach(i -> getContext().actorOf(EventDetailWorkerActor.props(eventIndexWorkerRouter, eventIndexTimeout), "edw" + i));
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder().build();
    }

    public static Props props(ActorRef eventIndexWorkerRouter, int noOfWorkers) {
        return Props.create(EventDetailWorkerCreator.class, () -> new EventDetailWorkerCreator(eventIndexWorkerRouter, noOfWorkers));
    }
}
