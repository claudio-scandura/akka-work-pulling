package com.mylaesoftware.actors;


import akka.actor.AbstractActor;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.japi.Creator;

import java.util.Iterator;
import java.util.Objects;
import java.util.stream.IntStream;

import static akka.japi.pf.ReceiveBuilder.match;


@SuppressWarnings("unchecked")
public class WorkDispatcher<T, E> extends AbstractActor {

    public static final String NAME = "work-dispatcher";
    private final int numberOfWorkers;
    private final ProcessingResultProgressReporter<E> onCompleteCallback;
    private final ProcessingResultProgressReporter<E> progressReportCallback;
    private final Iterator<T> itemsToDispatch;
    Long itemsProcessed = 0L;
    private int numberOfWorkersWithNoMoreWork = 0;
    final ProcessingResult<E> processingResult;

    WorkDispatcher(Iterator<T> itemsToDispatch,  int numberOfWorkers, Creator<Worker<T, E>> workerCreator, E baseResult, ProcessingResult.ResultAccumulator<E> resultAccumulator, ProcessingResultProgressReporter<E> progressReportCallback, ProcessingResultProgressReporter<E> onCompleteCallback) {
        this.itemsToDispatch = itemsToDispatch;
        this.numberOfWorkers = numberOfWorkers;
        this.progressReportCallback = progressReportCallback;
        this.onCompleteCallback = onCompleteCallback;
        this.processingResult = new ProcessingResult<>(baseResult, resultAccumulator);

        defineActorBehaviour();

        spawnChildren(workerCreator);
    }

    private void defineActorBehaviour() {
        receive(match(WorkDoneGetMore.class, message -> {
            processingResult.addSuccess((E) message.resultOfWorkDone);
            progressReportCallback.reportProgress(++itemsProcessed, processingResult.getAccumulatedResult(), processingResult.getFailures());
            dispatchWorkIfAvailable();
        })
                .match(WorkFailedGetMore.class, message -> {
                    processingResult.addFailure(message.failureOfWorkDone);
                    progressReportCallback.reportProgress(++itemsProcessed, processingResult.getAccumulatedResult(), processingResult.getFailures());
                    dispatchWorkIfAvailable();
                })
                .match(GetWork.class, message -> dispatchWorkIfAvailable())
                .build());
    }

    private void dispatchWorkIfAvailable() {
        if (this.itemsToDispatch.hasNext()) {
            sender().tell(new Worker.Work(itemsToDispatch.next()), self());
        } else {
            sender().tell(new Worker.NoMoreWork(), self());
            if (++numberOfWorkersWithNoMoreWork >= this.numberOfWorkers) {
                onCompleteCallback.reportProgress(itemsProcessed, processingResult.getAccumulatedResult(), processingResult.getFailures());
                self().tell(PoisonPill.getInstance(), self());
            }
        }
    }

    void spawnChildren(Creator<Worker<T, E>> workerCreator) {
        IntStream.range(0, numberOfWorkers).forEach(i -> context().actorOf(Props.create(Worker.class, workerCreator::create)));
    }

    public static <T, E> Props props(Iterator<T> itemsToDispatch, int numberOfWorkers, Creator<Worker<T, E>> workerCreator, E baseResult, ProcessingResult.ResultAccumulator<E> resultAccumulator, ProcessingResultProgressReporter<E> progressReportCallback, ProcessingResultProgressReporter<E> onCompleteCallback) {
        if (!itemsToDispatch.hasNext()) {
            throw new IllegalArgumentException("itemsToDispatch must not be empty");
        }
        if (numberOfWorkers <= 0) {
            throw new IllegalArgumentException("numberOfWorkers must be greater than zero");
        }

        return Props.create(WorkDispatcher.class, itemsToDispatch, numberOfWorkers, workerCreator, baseResult, resultAccumulator, progressReportCallback, onCompleteCallback);
    }

    static class WorkDoneGetMore extends GetWork {
        final Object resultOfWorkDone;

        WorkDoneGetMore(Object resultOfWorkDone) {
            this.resultOfWorkDone = Objects.requireNonNull(resultOfWorkDone, "resultOfWorkDone must not be null");
        }

    }

    static class WorkFailedGetMore extends GetWork {
        final Throwable failureOfWorkDone;

        WorkFailedGetMore(Throwable failureOfWorkDone) {
            this.failureOfWorkDone = Objects.requireNonNull(failureOfWorkDone, "failureOfWorkDone must not be null");
        }

    }

    static class GetWork {
    }

}
