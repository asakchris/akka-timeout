package com.example.messages;

import akka.actor.ActorRef;
import com.example.scala.messages.EventDetail;
import scala.concurrent.Promise;

import java.io.Serializable;

public class StreamResponse {
    public static class StreamInitialized implements Serializable {
        private Promise<Integer> promise;
        private ActorRef actor;

        public StreamInitialized() {
        }

        public StreamInitialized(Promise<Integer> promise) {
            this.promise = promise;
        }

        public StreamInitialized(ActorRef actor) {
            this.actor = actor;
        }

        public Promise<Integer> getPromise() {
            return promise;
        }

        public ActorRef getActor() {
            return actor;
        }
    }

    public static class StreamCompleted implements Serializable {
        private EventDetail eventDetail;

        public StreamCompleted() {
        }

        public StreamCompleted(EventDetail eventDetail) {
            this.eventDetail = eventDetail;
        }

        public EventDetail getEventDetail() {
            return eventDetail;
        }
    }

    public static class StreamFailure implements Serializable {
        private final Throwable cause;
        private EventDetail eventDetail;

        public StreamFailure(Throwable cause) {
            this.cause = cause;
        }

        public StreamFailure(Throwable cause, EventDetail eventDetail) {
            this.cause = cause;
            this.eventDetail = eventDetail;
        }

        public Throwable getCause() {
            return cause;
        }

        public EventDetail getEventDetail() {
            return eventDetail;
        }
    }
}
