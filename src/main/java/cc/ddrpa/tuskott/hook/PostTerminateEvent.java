package cc.ddrpa.tuskott.hook;

import cc.ddrpa.tuskott.tus.provider.FileInfo;

public class PostTerminateEvent extends TusEvent {

    private FileInfo upload;

    public PostTerminateEvent(FileInfo upload) {
        this.upload = upload;
    }

    public FileInfo upload() {
        return upload;
    }
}