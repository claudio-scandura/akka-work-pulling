package com.mylaesoftware.actors;

import akka.actor.AbstractActor;
import akka.actor.PoisonPill;
import akka.dispatch.OnComplete;
import com.mylaesoftware.actors.WorkDispatcher.GetWork;
import scala.concurrent.ExecutionContextExecutor;
import scala.concurrent.Future;

import java.util.function.BiFunction;

import static akka.japi.pf.ReceiveBuilder.match;
import static com.mylaesoftware.actors.WorkDispatcher.WorkDoneGetMore;
import static com.mylaesoftware.actors.WorkDispatcher.WorkFailedGetMore;
import static java.util.Optional.ofNullable;

@SuppressWarnings("unchecked")
public class Worker<T, E> extends AbstractActor {


    public final BiFunction<T, ExecutionContextExecutor, Future<E>> workProcessingFunction;

    public Worker(BiFunction<T, ExecutionContextExecutor, Future<E>> workProcessing) {
        workProcessingFunction = workProcessing;
        receive(
                match(Work.class, message -> doWorkAndAskForMore((T) message.work))
                .match(NoMoreWork.class, message -> self().tell(PoisonPill.getInstance(), self()))
                .build()
        );

        context().parent().tell(new GetWork(), self());
    }

    private void doWorkAndAskForMore(T work) {
        workProcessingFunction.apply(work, context().dispatcher()).onComplete(new OnComplete<E>() {
            @Override
            public void onComplete(Throwable failure, E success) throws Throwable {

                GetWork message = ofNullable(success)
                        .map( s ->  (GetWork) new WorkDoneGetMore(s))
                        .orElseGet(() -> new WorkFailedGetMore(failure));

                context().parent().tell(message, self());
            }
        }, context().dispatcher());
    }

    static class NoMoreWork {
    }

    static class Work<T> {
        T work;

        Work(T work) {
            this.work = work;
        }
    }
}
