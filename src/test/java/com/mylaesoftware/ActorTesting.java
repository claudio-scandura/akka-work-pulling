package com.mylaesoftware;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.TestActorRef;

import java.util.UUID;

public interface ActorTesting {

    ActorSystem getActorSystem();

    default <T extends AbstractActor> void withTestActor(Props props, ActorRef supervisor, UnsafeConsumer<TestActorRef<T>> testBody) throws Exception {
        TestActorRef<T> actorRef = TestActorRef.create(getActorSystem(), props, supervisor, "test-actor-" + UUID.randomUUID());
        runTest(actorRef, testBody);
    }

    default <T extends AbstractActor> void withTestActor(Props props, UnsafeConsumer<TestActorRef<T>> testBody) throws Exception {
        TestActorRef<T> actorRef = TestActorRef.create(getActorSystem(), props, "test-actor-" + UUID.randomUUID());
        runTest(actorRef, testBody);
    }

    default <T extends AbstractActor> void runTest (TestActorRef<T> actorRef, UnsafeConsumer<TestActorRef<T>> runTest) throws  Exception {
        try {
            runTest.accept(actorRef);
        } finally {
            actorRef.stop();
        }
    }
}
