package com.mylaesoftware;

public interface ExceptionTesting {

    default <T extends Throwable> T expect(Class<T> exceptionClass, AssertionBlock assertion) throws Exception {
        try {
            assertion._assert();
            throw new AssertionError("Expected a " + exceptionClass.getName() + ", but no exception was thrown");
        } catch (Throwable t) {
            if (exceptionClass.isInstance(t)) {
                return exceptionClass.cast(t);
            }
            throw t;
        }
    }

}
