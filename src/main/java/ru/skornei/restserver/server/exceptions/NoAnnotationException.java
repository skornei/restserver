package ru.skornei.restserver.server.exceptions;

public class NoAnnotationException extends RuntimeException {

    public NoAnnotationException(String message) {
        super(message + ".java");
    }
}
