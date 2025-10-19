// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Server/src/main/java/com/networkopsim/server/handling/ExceptionHandler.java
// ================================================================================

package com.networkopsim.server.handling;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method within a @ControllerAdvice class as a handler for specific exceptions.
 * The 'value' attribute specifies an array of Throwable classes that this method can handle.
 */
@Target(ElementType.METHOD) // This annotation can be applied to methods.
@Retention(RetentionPolicy.RUNTIME) // This annotation will be available at runtime for reflection.
public @interface ExceptionHandler {
    /**
     * An array of exception types that this handler is responsible for.
     * @return The array of exception classes.
     */
    Class<? extends Throwable>[] value();
}