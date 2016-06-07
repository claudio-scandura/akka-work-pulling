package com.mylaesoftware.actors;

import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.dispatch.Futures;
import akka.testkit.JavaTestKit;
import akka.testkit.TestProbe;
import com.mylaesoftware.ActorTesting;
import com.mylaesoftware.Eventually;
import com.mylaesoftware.actors.WorkDispatcher.GetWork;
import com.mylaesoftware.actors.WorkDispatcher.WorkDoneGetMore;
import com.mylaesoftware.actors.WorkDispatcher.WorkFailedGetMore;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.ExecutionContextExecutor;
import scala.concurrent.Future;

import java.util.function.BiFunction;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class WorkerTest implements Eventually, ActorTesting {


    private static ActorSystem _system;


    @Override
    public ActorSystem getActorSystem() {
        return _system;
    }

    private final TestProbe dispatcherProbe = new TestProbe(_system);

    @BeforeClass
    public static void setupAkka() {
        _system = ActorSystem.create();
    }

    @AfterClass
    public static void teardown() {
        JavaTestKit.shutdownActorSystem(_system);
    }

    private BiFunction<String, ExecutionContextExecutor, Future<? extends String>> workProcessingFunctionStub =
            (first, second) -> Futures.successful("WORKED");

    @Test
    public void onNoMoreWork_workerShouldSuicide() throws Exception {
        Props props = Props.create(Worker.class, workProcessingFunctionStub);
        withTestActor(props, dispatcherProbe.ref(), testActor -> {
            testActor.tell(new Worker.NoMoreWork(), null);
            eventually(() -> assertTrue(testActor.isTerminated()));
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    public void onWork_workerShouldInvokeProcessFunctionAndAskForMoreWhenItCompletes() throws Exception {
        Props props = Props.create(Worker.class, workProcessingFunctionStub);
        withTestActor(props, dispatcherProbe.ref(),  testActor -> {
            dispatcherProbe.expectMsgClass(GetWork.class);

            testActor.tell(new Worker.Work<>("WORK!"), null);

            WorkDoneGetMore moreWork = dispatcherProbe.expectMsgClass(WorkDoneGetMore.class);

            assertThat(moreWork.resultOfWorkDone, is("WORKED"));

        });
    }

    @SuppressWarnings("unchecked")
    @Test
    public void worker_shouldAskForWorkWhenCreated() throws Exception {
        Props props = Props.create(Worker.class, workProcessingFunctionStub);
        withTestActor(props, dispatcherProbe.ref(), testActor -> {
            dispatcherProbe.expectMsgClass(GetWork.class);
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    public void onWork_workerShouldInvokeProcessFunctionAndAskForMoreWhenItCompletesWithFailure() throws Exception {
        RuntimeException expectedException = new RuntimeException("KABOOM");
        workProcessingFunctionStub = (first, second) -> Futures.failed(expectedException);
        Props props = Props.create(Worker.class, workProcessingFunctionStub);

        withTestActor(props, dispatcherProbe.ref(), testActor -> {
            dispatcherProbe.expectMsgClass(GetWork.class);

            testActor.tell(new Worker.Work<>("WORK!"), null);

            WorkFailedGetMore moreWork = dispatcherProbe.expectMsgClass(WorkFailedGetMore.class);

            assertThat(moreWork.failureOfWorkDone, is(expectedException));
        });
    }
}