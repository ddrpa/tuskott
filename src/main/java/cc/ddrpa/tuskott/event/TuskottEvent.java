package cc.ddrpa.tuskott.event;

import java.time.LocalDateTime;

public abstract class TuskottEvent {

    private final LocalDateTime eventTime = LocalDateTime.now();

    public LocalDateTime eventTime() {
        return eventTime;
    }
}