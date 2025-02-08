package cc.ddrpa.tuskott.event;

import java.lang.reflect.Method;

public record EventCallback(Object bean,
                            Method method) {

}