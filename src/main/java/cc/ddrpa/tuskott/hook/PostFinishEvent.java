package cc.ddrpa.tuskott.hook;

import cc.ddrpa.tuskott.tus.provider.FileInfo;

public class PostFinishEvent extends TusEvent {

    private FileInfo upload;

    public PostFinishEvent(FileInfo upload) {
        this.upload = upload;
    }

    public FileInfo upload() {
        return upload;
    }
}