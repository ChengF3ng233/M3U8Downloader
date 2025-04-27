package cn.feng.m3u8;

import java.util.List;

/**
 * @author ChengFeng
 * @since 2024/3/23
 **/
public class M3U8Video {
    private final String title;
    private String videoURL;
    private List<String> tsList;
    private byte[] tsKey;
    private byte[] tsIv;

    public M3U8Video(String title, String videoURL) {
        this.title = title;
        this.videoURL = videoURL;
    }

    public void setVideoURL(String videoURL) {
        this.videoURL = videoURL;
    }

    public void setTsList(List<String> tsList) {
        this.tsList = tsList;
    }

    public void setTsKey(byte[] tsKey) {
        this.tsKey = tsKey;
    }

    public void setTsIv(byte[] tsIv) {
        this.tsIv = tsIv;
    }

    public String getTitle() {
        return title;
    }

    public String getVideoURL() {
        return videoURL;
    }

    public List<String> getTsList() {
        return tsList;
    }

    public byte[] getTsKey() {
        return tsKey;
    }

    public byte[] getTsIv() {
        return tsIv;
    }

    @Override
    public String toString() {
        return title;
    }
}
