package cc.ddrpa.tuskott.event;

import cc.ddrpa.tuskott.tus.resource.UploadResource;

public class PostCompleteEvent extends TuskottEvent {

    private final UploadResource uploadResource;

    public PostCompleteEvent(UploadResource uploadResource) {
        this.uploadResource = uploadResource;
    }

    public UploadResource getUploadResource() {
        return uploadResource;
    }
}