package cc.ddrpa.tuskott.event;

import cc.ddrpa.tuskott.tus.resource.UploadResource;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public class PostCompleteEvent extends TuskottEvent {

    @Getter
    private final UploadResource uploadResource;
}