package com.example.system;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.cluster.singleton.ClusterSingletonManager;
import akka.cluster.singleton.ClusterSingletonManagerSettings;
import akka.management.javadsl.AkkaManagement;
import akka.routing.FromConfig;
import com.example.actors.EventDetailActor;
import com.example.actors.EventDetailWorkerCreator;
import com.example.actors.EventDispatcherActor;
import com.example.actors.EventIndexWorkerCreator;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class EventMain {
    public static void main(String[] args) {
        String applicationName = "event-processor";
        //Config config = ConfigFactory.parseString("akka.remote.artery.canonical.port=" + port).withFallback(ConfigFactory.load());
        Config config = ConfigFactory.load();
        //System.out.println(config.root().render()); //Useful for printing out the actual config info loaded.
        final ActorSystem actorSystem = ActorSystem.create(applicationName, config);
        ClusterSingletonManagerSettings settings = ClusterSingletonManagerSettings.create(actorSystem);

        int noOfEventDetailWorkers = config.getInt("event.noOfWorkers.eventDetail");
        int noOfEventIndexWorkers = config.getInt("event.noOfWorkers.eventIndex");
        System.out.println("noOfEventDetailWorkers: " + noOfEventDetailWorkers + ", noOfEventIndexWorkers: " + noOfEventIndexWorkers);

        actorSystem.actorOf(EventIndexWorkerCreator.props(noOfEventIndexWorkers), "event-index-workers");
        ActorRef eventIndexWorkerRouter = actorSystem.actorOf(FromConfig.getInstance().props(), "event-index-router");
        actorSystem.actorOf(EventDetailWorkerCreator.props(eventIndexWorkerRouter, noOfEventDetailWorkers), "event-detail-workers");

        ActorRef eventDetailActor = actorSystem.actorOf(EventDetailActor.props(), "EventDetailActor");
        actorSystem.actorOf(ClusterSingletonManager.props(EventDispatcherActor.props(eventDetailActor), PoisonPill.getInstance(), settings), "EventDispatcherActor");

        AkkaManagement.get(actorSystem).start();
    }
}
