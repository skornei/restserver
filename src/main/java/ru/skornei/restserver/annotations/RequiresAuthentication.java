package ru.skornei.restserver.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
public @interface RequiresAuthentication {
}
