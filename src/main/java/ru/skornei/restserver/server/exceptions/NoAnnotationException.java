package ru.skornei.restserver.server.exceptions;

public class NoAnnotationException extends RuntimeException {

    public NoAnnotationException(String className, String annotationName) {
        super("Class " + className + ".java must be annotated with " + annotationName);
    }
}
