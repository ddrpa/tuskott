package cc.ddrpa.tuskott.event;

import cc.ddrpa.tuskott.tus.resource.UploadResource;

public class PostCreateEvent extends TuskottEvent {

    private final UploadResource uploadResource;

    public PostCreateEvent(UploadResource uploadResource) {
        this.uploadResource = uploadResource;
    }

    public UploadResource getUploadResource() {
        return uploadResource;
    }
}