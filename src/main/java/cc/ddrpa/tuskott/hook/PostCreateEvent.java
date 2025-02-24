package cc.ddrpa.tuskott.hook;

import cc.ddrpa.tuskott.tus.provider.FileInfo;

public class PostCreateEvent extends TusEvent {

    private FileInfo upload;

    public PostCreateEvent(FileInfo upload) {
        this.upload = upload;
    }

    public FileInfo upload() {
        return upload;
    }
}