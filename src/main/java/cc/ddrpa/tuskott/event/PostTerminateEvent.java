package cc.ddrpa.tuskott.event;

import cc.ddrpa.tuskott.tus.resource.UploadResource;

public class PostTerminateEvent extends TuskottEvent {

    private final UploadResource uploadResource;

    public PostTerminateEvent(UploadResource uploadResource) {
        this.uploadResource = uploadResource;
    }

    public UploadResource getUploadResource() {
        return uploadResource;
    }
}