package com.mylaesoftware;

@FunctionalInterface
public interface UnsafeConsumer<U> {
    void accept(U u) throws Exception;
}
