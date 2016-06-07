package com.mylaesoftware.actors;

import java.util.Map;

@FunctionalInterface
public interface ProcessingResultProgressReporter<E> {

    void reportProgress(Long itemsProcessed, E accumulatedResult, Map<Class<? extends Throwable>, Integer> errors);
}
