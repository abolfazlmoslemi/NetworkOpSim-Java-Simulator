// FILE: /NetworkOpSim-Multiplayer/NetworkOpSim-Server/src/main/java/com/networkopsim/server/handling/ExceptionDispatcher.java
// ================================================================================

package com.networkopsim.server.handling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;

/**
 * Discovers and invokes appropriate @ExceptionHandler methods from a @ControllerAdvice class.
 * This class scans a given package at startup to find the handler component.
 */
public class ExceptionDispatcher {
    private static final Logger log = LoggerFactory.getLogger(ExceptionDispatcher.class);

    private Object adviceInstance;
    private final List<HandlerMethod> handlerMethods = new ArrayList<>();

    /**
     * Represents a discovered method annotated with @ExceptionHandler.
     */
    private static class HandlerMethod {
        final Method method;
        final Class<? extends Throwable>[] exceptionTypes;

        HandlerMethod(Method method, Class<? extends Throwable>[] exceptionTypes) {
            this.method = method;
            this.exceptionTypes = exceptionTypes;
        }
    }

    /**
     * Constructs an ExceptionDispatcher and scans the specified package for handlers.
     * @param packageName The package to scan (e.g., "com.networkopsim.server.handling").
     */
    public ExceptionDispatcher(String packageName) {
        scanForHandlers(packageName);
        if (adviceInstance == null) {
            log.warn("No @ControllerAdvice class found in package '{}'. Global exception handling will be disabled.", packageName);
        }
    }

    /**
     * Scans the given package for a class annotated with @ControllerAdvice and populates handler methods.
     */
    private void scanForHandlers(String packageName) {
        try {
            List<Class<?>> classes = getClasses(packageName);
            for (Class<?> cls : classes) {
                if (cls.isAnnotationPresent(ControllerAdvice.class)) {
                    this.adviceInstance = cls.getDeclaredConstructor().newInstance();
                    log.info("Found @ControllerAdvice class: {}", cls.getName());
                    for (Method method : cls.getDeclaredMethods()) {
                        if (method.isAnnotationPresent(ExceptionHandler.class)) {
                            ExceptionHandler handlerAnnotation = method.getAnnotation(ExceptionHandler.class);
                            method.setAccessible(true); // Ensure we can invoke the method
                            handlerMethods.add(new HandlerMethod(method, handlerAnnotation.value()));
                            log.debug("Registered @ExceptionHandler method: {}", method.getName());
                        }
                    }
                    // Assume only one @ControllerAdvice class per application for simplicity
                    return;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize ExceptionDispatcher by scanning package " + packageName, e);
        }
    }

    /**
     * Finds the most specific handler for the given exception and invokes it.
     * @param exception The exception that occurred.
     * @return The object returned by the handler method (e.g., a ServerResponse), or null if no handler is found.
     */
    public Object dispatch(Throwable exception) {
        if (adviceInstance == null) {
            log.error("Exception occurred but no @ControllerAdvice is configured. Exception will be re-thrown.", exception);
            return null; // Or re-throw the exception
        }

        try {
            HandlerMethod bestMatch = null;
            int minDistance = Integer.MAX_VALUE;

            // Find the handler method that is the closest superclass of the actual exception
            for (HandlerMethod handler : handlerMethods) {
                for (Class<? extends Throwable> handledType : handler.exceptionTypes) {
                    if (handledType.isAssignableFrom(exception.getClass())) {
                        int distance = getInheritanceDistance(exception.getClass(), handledType);
                        if (distance < minDistance) {
                            minDistance = distance;
                            bestMatch = handler;
                        }
                    }
                }
            }

            if (bestMatch != null) {
                log.debug("Dispatching exception {} to handler {}", exception.getClass().getSimpleName(), bestMatch.method.getName());
                return bestMatch.method.invoke(adviceInstance, exception);
            } else {
                log.warn("No suitable @ExceptionHandler found for exception: {}", exception.getClass().getName());
            }
        } catch (Exception e) {
            log.error("Critical error within ExceptionDispatcher while handling an exception.", e);
        }
        return null;
    }

    /**
     * Calculates the "distance" in the inheritance hierarchy between two classes.
     */
    private int getInheritanceDistance(Class<?> actual, Class<?> target) {
        int distance = 0;
        Class<?> current = actual;
        while (current != null && current != target) {
            current = current.getSuperclass();
            distance++;
        }
        return (current == null) ? Integer.MAX_VALUE : distance;
    }

    /**
     * Scans all classes accessible from the context class loader which belong to the given package.
     */
    private List<Class<?>> getClasses(String packageName) throws Exception {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        String path = packageName.replace('.', '/');
        Enumeration<URL> resources = classLoader.getResources(path);
        List<File> dirs = new ArrayList<>();
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            URI uri = new URI(resource.toString());
            dirs.add(new File(uri.getPath()));
        }
        ArrayList<Class<?>> classes = new ArrayList<>();
        for (File directory : dirs) {
            classes.addAll(findClasses(directory, packageName));
        }
        return classes;
    }

    /**
     * Recursive method used to find all classes in a given directory and subdirs.
     */
    private List<Class<?>> findClasses(File directory, String packageName) throws ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();
        if (!directory.exists()) {
            return classes;
        }
        File[] files = directory.listFiles();
        for (File file : Objects.requireNonNull(files)) {
            if (file.isDirectory()) {
                classes.addAll(findClasses(file, packageName + "." + file.getName()));
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + '.' + file.getName().substring(0, file.getName().length() - 6);
                classes.add(Class.forName(className));
            }
        }
        return classes;
    }
}