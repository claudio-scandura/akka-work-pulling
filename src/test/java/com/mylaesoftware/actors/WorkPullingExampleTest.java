package com.mylaesoftware.actors;

import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.dispatch.Futures;
import akka.japi.Creator;
import com.typesafe.config.ConfigFactory;
import org.junit.Test;

import java.util.logging.Logger;
import java.util.stream.IntStream;

public class WorkPullingExampleTest {

    Logger logger = Logger.getLogger("logger");
    private Long counter = System.currentTimeMillis();

    ProcessingResultProgressReporter<Integer> progressReporter = (items, partialResult, errors) -> {
        if (items % 1000 == 0) {
            long now = System.currentTimeMillis();
            long elapsed = (now - counter);
            counter = now;
            logger.info(() -> "Batch processed in: " + elapsed + " millis items: " + items + " result accumulated: " + partialResult + " errors: " + errors.size());
        }
    };

    @Test
    public void shouldRunWorkPullingExample() throws Exception {
        ActorSystem _system = ActorSystem.create("example", ConfigFactory.load());

        IntStream work = IntStream.range(0, 1000000);

        Props dispatcherProps = WorkDispatcher.props(work.iterator(), 512, getWorkerCreator(), 0, Integer::sum, progressReporter, progressReporter);
        _system.actorOf(dispatcherProps, WorkDispatcher.NAME);

        Thread.sleep(Long.MAX_VALUE);
    }

    private Creator<Worker<Integer, Integer>> getWorkerCreator() {
        return () -> new Worker<>((index, ec) -> Futures.future(() -> {

            try {
                Thread.sleep((long) (Math.random() * 100));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return 1;
        }, ec));
    }

}
