// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Server/src/main/java/com/networkopsim/server/handling/ControllerAdvice.java
// ================================================================================

package com.networkopsim.server.handling;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a global exception handling component, similar to Spring's @ControllerAdvice.
 * The ExceptionDispatcher will scan for a class with this annotation to find exception handlers.
 */
@Target(ElementType.TYPE) // This annotation can be applied to classes.
@Retention(RetentionPolicy.RUNTIME) // This annotation will be available at runtime for reflection.
public @interface ControllerAdvice {
}