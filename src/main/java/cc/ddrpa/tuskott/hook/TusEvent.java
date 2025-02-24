package cc.ddrpa.tuskott.hook;

import java.time.LocalDateTime;

public abstract class TusEvent {

    private LocalDateTime eventTime = LocalDateTime.now();

    public LocalDateTime eventTime() {
        return eventTime;
    }
}