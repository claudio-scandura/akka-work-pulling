package com.mylaesoftware.actors;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.japi.Creator;
import akka.testkit.JavaTestKit;
import akka.testkit.TestActor;
import com.mylaesoftware.ActorTesting;
import com.mylaesoftware.Eventually;
import com.mylaesoftware.ExceptionTesting;
import com.mylaesoftware.actors.WorkDispatcher.WorkDoneGetMore;
import org.junit.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

import static com.mylaesoftware.actors.WorkDispatcher.*;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.*;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.verify;

@SuppressWarnings({"ThrowableResultOfMethodCallIgnored", "unchecked"})
public class WorkDispatcherTest implements Eventually, ExceptionTesting, ActorTesting {


    private static ActorSystem _system;

    @Override
    public ActorSystem getActorSystem() {
        return _system;
    }

    private final Iterator<String> itemsToDispatch = Collections.singletonList("WORK!").iterator();

    @Mock
    private ProcessingResultProgressReporter onCompleteCallbackMock;

    @Mock
    private ProcessingResultProgressReporter progressReportCallback;

    private final JavaTestKit childrenAutoPilot = new JavaTestKit(_system);

    @BeforeClass
    public static void setupAkka() {
        _system = ActorSystem.create();
    }

    @AfterClass
    public static void teardown() {
        JavaTestKit.shutdownActorSystem(_system);
    }

    @Before
    public void setup(){
        MockitoAnnotations.initMocks(this);
    }

    private Creator<Worker<String, Integer>> getFailingWorkerCreator() {
        return () -> {
            Assert.fail("Worker creator must not be called");
            return new Worker<>(null);
        };
    }

    @Test
    public void onWorkDoneGetMore_shouldTellChildNoMoreWork_whenNoItemsLeftToDispatch () throws Exception {
        setUpAutoPilot(childrenAutoPilot, new WorkDoneGetMore("DONE DEE".hashCode()));
        Props props = Props.create(TestWorkDispatcher.class, () -> new TestWorkDispatcher(itemsToDispatch, getFailingWorkerCreator(), progressReportCallback, onCompleteCallbackMock));

        withTestActor(props, testActor -> {
            childrenAutoPilot.send(testActor, new GetWork());
            childrenAutoPilot.expectMsgClass(Worker.Work.class);
            childrenAutoPilot.expectMsgClass(Worker.NoMoreWork.class);
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    public void onWorkDoneGetMore_shouldCallOnCompleteCallbackAndCommitSuicide_whenNoItemsLeftToDispatchAndNoMoreChildrenAreWorking() throws Exception {
        setUpAutoPilot(childrenAutoPilot,  new WorkDoneGetMore("DONE DEE".hashCode()));
        Props props = Props.create(TestWorkDispatcher.class, () -> new TestWorkDispatcher(itemsToDispatch, getFailingWorkerCreator(), progressReportCallback, onCompleteCallbackMock));

        withTestActor(props, testActor -> {
            childrenAutoPilot.send(testActor, new GetWork());
            ArgumentCaptor<Map> failuresCaptor = ArgumentCaptor.forClass(Map.class);
            eventually(() -> {
                verify(onCompleteCallbackMock).reportProgress(anyLong(), isA(Integer.class), failuresCaptor.capture());

                assertThat(failuresCaptor.getValue().size(), is(0));
                assertTrue(testActor.isTerminated());
            });
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    public void onWorkFailedGetMore_shouldCallOnCompleteCallbackAndCommitSuicide_whenNoItemsLeftToDispatchAndNoMoreChildrenAreWorking() throws Exception {
        setUpAutoPilot(childrenAutoPilot,  new WorkFailedGetMore(new RuntimeException("KABOOM")));
        Props props = Props.create(TestWorkDispatcher.class, () -> new TestWorkDispatcher(itemsToDispatch, getFailingWorkerCreator(), progressReportCallback, onCompleteCallbackMock));

        withTestActor(props, testActor -> {
            childrenAutoPilot.send(testActor, new GetWork());
            ArgumentCaptor<Map> failuresCaptor = ArgumentCaptor.forClass(Map.class);
            eventually(() -> {
                verify(onCompleteCallbackMock).reportProgress(anyLong(), isA(Integer.class), failuresCaptor.capture());

                assertThat(failuresCaptor.getValue().size(), is(1));
                assertTrue(testActor.isTerminated());
            });
        });
    }

    @Test
    public void onWorkFailedGetMore_shouldCorrectlyUpdateInternalState() throws Exception {
        RuntimeException failure = new RuntimeException("KABOOM");
        setUpAutoPilot(childrenAutoPilot,  new WorkFailedGetMore(failure));
        Props props = Props.create(TestWorkDispatcher.class, () -> new TestWorkDispatcher(itemsToDispatch, getFailingWorkerCreator(), progressReportCallback, onCompleteCallbackMock));

        withTestActor(props, testActor -> {
            WorkDispatcher<String, Integer> dispatcher = (WorkDispatcher<String, Integer>) testActor.underlyingActor();
            childrenAutoPilot.send(testActor, new GetWork());
            childrenAutoPilot.expectMsgClass(Worker.Work.class);
            assertThat(dispatcher.itemsProcessed, is(1L));
            Map<Class<? extends Throwable>, Integer> failures = dispatcher.processingResult.getFailures();
            assertThat(failures.size(), is(1));
            assertThat(failures, hasEntry(RuntimeException.class, 1));
        });
    }

    @Test
    public void onWorkDoneGetMore_shouldCorrectlyUpdateInternalState() throws Exception {
        int resultOfWorkDone = "DONE DEE".hashCode();
        setUpAutoPilot(childrenAutoPilot,  new WorkDoneGetMore(resultOfWorkDone));
        Props props = Props.create(TestWorkDispatcher.class, () -> new TestWorkDispatcher(itemsToDispatch, getFailingWorkerCreator(), progressReportCallback, onCompleteCallbackMock));

        withTestActor(props, testActor -> {
            WorkDispatcher<String, Integer> dispatcher = (WorkDispatcher<String, Integer>) testActor.underlyingActor();
            childrenAutoPilot.send(testActor, new GetWork());
            childrenAutoPilot.expectMsgClass(Worker.Work.class);
            assertThat(dispatcher.itemsProcessed, is(1L));
            assertThat(dispatcher.processingResult.getAccumulatedResult(), is(resultOfWorkDone));
        });
    }

    @Test
    public void props_shouldThrowIllegalArgumentException_whenItemsToDispatchIsEmpty() throws Exception {

        IllegalArgumentException exception = expect(IllegalArgumentException.class,
                () -> props(Collections.emptyIterator(), 1, null, 0, null, progressReportCallback, onCompleteCallbackMock));

        assertThat(exception.getMessage(), containsString("itemsToDispatch"));
        assertThat(exception.getMessage(), containsString("must not be empty"));
    }

    @Test
    public void props_shouldThrowIllegalArgumentException_whenNumberOfWorkersProvidedIsNotGreaterThanZero() throws Exception {

        IllegalArgumentException exception = expect(IllegalArgumentException.class,
                () -> props(itemsToDispatch, 0, null, 0, null, progressReportCallback, onCompleteCallbackMock));

        assertThat(exception.getMessage(), containsString("numberOfWorkers"));
        assertThat(exception.getMessage(), containsString("must be greater than zero"));
    }


    private JavaTestKit setUpAutoPilot(JavaTestKit childrenAutoPilot, WorkDispatcher.GetWork replyToDispatcher) {
        return new JavaTestKit(_system){{
            childrenAutoPilot.setAutoPilot(new TestActor.AutoPilot() {
                @Override
                public TestActor.AutoPilot run(ActorRef sender, Object msg) {
                    if (msg instanceof Worker.NoMoreWork){
                        return noAutoPilot();
                    }
                    sender.tell(replyToDispatcher, childrenAutoPilot.getRef());
                    return keepRunning();
                }
            });
        }};
    }

    class TestWorkDispatcher extends WorkDispatcher<String, Integer>{

        TestWorkDispatcher(Iterator<String> itemsToDispatch, Creator<Worker<String, Integer>> workerCreator, ProcessingResultProgressReporter<Integer> progressReportCallback, ProcessingResultProgressReporter<Integer> onCompleteCallback) {
            super(itemsToDispatch, 1, workerCreator, 0, Integer::sum, progressReportCallback, onCompleteCallback);
        }

        @Override
        void spawnChildren(Creator<Worker<String, Integer>> workerCreator) {

        }
    }

}