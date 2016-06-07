package com.mylaesoftware.actors;


import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ProcessingResult<E> {

    private final Map<Class<? extends Throwable>, Integer> failures;
    private ResultAccumulator<E> resultAccumulator;
    private E partialResult;

    public ProcessingResult(E baseResult, ResultAccumulator<E> resultAccumulator) {
        this.partialResult = Objects.requireNonNull(baseResult, "baseResult must not be null");
        this.resultAccumulator = Objects.requireNonNull(resultAccumulator, "resultAccumulator must not be null");
        failures = new HashMap<>();
    }

    public void addSuccess(E successfulResult) {
        partialResult = resultAccumulator.accumulate(partialResult, successfulResult);
    }

    public void addFailure(Throwable failure) {
        Integer count = failures.getOrDefault(failure.getClass(), 0);
        failures.put(failure.getClass(), ++count);
    }

    public E getAccumulatedResult() {
        return partialResult;
    }

    public Map<Class<? extends Throwable>, Integer> getFailures() {
        return failures;
    }

    @FunctionalInterface
    public interface ResultAccumulator<E> {
        E accumulate(E partialResult, E resultToAccumulate);
    }
}
