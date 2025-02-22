package cc.ddrpa.tuskott.hook;

import java.lang.reflect.Method;

public record EventCallback(Object bean,
                            Method method) {

}