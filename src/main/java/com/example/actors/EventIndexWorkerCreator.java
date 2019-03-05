package com.example.actors;

import akka.actor.AbstractLoggingActor;
import akka.actor.Props;

import java.util.stream.IntStream;

public class EventIndexWorkerCreator extends AbstractLoggingActor {
    private final int noOfWorkers;

    public EventIndexWorkerCreator(int noOfWorkers) {
        this.noOfWorkers = noOfWorkers;
    }

    @Override
    public void preStart() {
        long timeout = context().system().settings().config().getLong("event.timeout-sec.event-detail-worker");
        log().info("EventIndexWorkerActor actor timeout: {}", timeout);
        IntStream
                .rangeClosed(1, noOfWorkers)
                .forEach(i -> getContext().actorOf(EventIndexWorkerActor.props(timeout), "w" + i));
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder().build();
    }

    public static Props props(int noOfWorkers) {
        return Props.create(EventIndexWorkerCreator.class, () -> new EventIndexWorkerCreator(noOfWorkers));
    }
}
