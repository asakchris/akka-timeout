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
        IntStream
                .rangeClosed(1, noOfWorkers)
                .forEach(i -> getContext().actorOf(EventIndexWorkerActor.props(), "w" + i));
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder().build();
    }

    public static Props props(int noOfWorkers) {
        return Props.create(EventIndexWorkerCreator.class, () -> new EventIndexWorkerCreator(noOfWorkers));
    }
}
