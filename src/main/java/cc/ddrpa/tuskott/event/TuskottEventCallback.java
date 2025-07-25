package cc.ddrpa.tuskott.event;

import java.lang.reflect.Method;

public record TuskottEventCallback(Object bean,
                                   Method method) {

}