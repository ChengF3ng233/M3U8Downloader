package cn.feng.m3u8;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

import static cn.feng.m3u8.Util.*;

/**
 * A downloader for m3u8 videos.<br>
 * Call {@link M3U8Downloader#init(int, int)} before constructor method.<br>
 * To show debug messages, set LEVEL in log4j.properties(or log4j2.xml) to DEBUG
 * @author ChengFeng
 * @since 2024/3/23
 **/
public class M3U8Downloader {
    private OkHttpClient client;
    private final OkHttpClient.Builder builder = new OkHttpClient.Builder().connectTimeout(5000, TimeUnit.SECONDS).retryOnConnectionFailure(true);

    private final Set<M3U8Video> currentTaskList = new HashSet<>();
    private AtomicInteger currentProgress = new AtomicInteger();

    private File outputDir = new File("download");
    private boolean split;
    private boolean m3u8;
    private boolean mp4 = true; // Download mp4 in default.
    private IntConsumer onBegin;
    private IntConsumer onProgress;
    private Runnable onComplete;

    public static void init(int videoThreadCount, int tsThreadCount) {
        MultiThreads.launch(videoThreadCount, tsThreadCount);
    }

    private void checkMultiThreads() {
        if (MultiThreads.videoExecutor == null || MultiThreads.tsExecutor == null) {
            throw new IllegalStateException("Method \"init(int, int)\" must be called in advance.");
        }
    }

    public M3U8Downloader() {
        checkMultiThreads();
        client = builder.build();
    }

    public M3U8Downloader(Proxy proxy) {
        checkMultiThreads();
        client = builder.proxy(proxy).build();
    }

    /**
     * Remove current proxy.
     */
    public void clearProxy() {
        client = builder.proxy(Proxy.NO_PROXY).build();
    }

    /**
     * Set up a proxy for the downloader.
     */
    public void proxy(Proxy proxy) {
        client = builder.proxy(proxy).build();
    }

    /**
     * Resolve single video.
     */
    public void resolve(M3U8Video video) {
        if (video.getTsList() != null && !video.getTsList().isEmpty()) return;

        logger.debug("Resolving video: {}", video.getTitle());

        String m3u8Str = httpString(video.getVideoURL());
        List<String> ts = new ArrayList<>();
        for (String line : m3u8Str.split("\n")) {
            if (line.startsWith("#EXT-X-KEY")) {
                String[] values = line.split(",");

                video.setTsKey(httpBytes(values[1].replace("URI=", "").replaceAll("\"", "")));
                // Default iv: 0x00
                video.setTsIv(hexStringToByteArray(values.length > 2 ? values[2].replace("IV=", "") : "0x00000000000000000000000000000000"));
            } else if (line.contains(".ts")) {
                ts.add(line);
            }
        }
        video.setTsList(ts);
    }

    /**
     * Resolve a list of video.
     */
    public void resolve(List<M3U8Video> videoList) {
        CountDownLatch latch = new CountDownLatch(videoList.size());

        for (M3U8Video video : videoList) {
            MultiThreads.videoExecutor.execute(() -> {
                resolve(video);
                latch.countDown();
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Download single video.
     * @return The download process.
     */
    public Future<?> download(M3U8Video video) {
        // Remove duplicated task
        if (currentTaskList.contains(video)) return null; else currentTaskList.add(video);

        // Resolve video
        resolve(video);

        AtomicInteger counter = new AtomicInteger();
        currentTaskList.forEach(task -> counter.addAndGet(task.getTsList().size()));

        if (onBegin != null) {
            onBegin.accept(counter.get());
        }

        return MultiThreads.videoExecutor.submit(() -> {
            try {
                // Output
                File mp4Dir = new File(outputDir, "mp4s");
                File m3u8Dir = new File(outputDir, "m3u8s");
                File mp4File = new File(split ? mp4Dir : outputDir, sanitizeFileName(video.getTitle()) + ".mp4");
                File m3u8File = new File(split ? m3u8Dir : outputDir, sanitizeFileName(video.getTitle()) + ".m3u8");

                if (split) {
                    mkdirs(mp4Dir, m3u8Dir);
                }
                mkdirs(outputDir);

                logger.debug("Downloading {}", video.getTitle());

                // Download mp4
                if (mp4) {
                    // Download ts
                    List<String> tsList = video.getTsList();
                    Map<Integer, byte[]> tsParts = new ConcurrentHashMap<>();

                    CountDownLatch tsLatch = new CountDownLatch(tsList.size());
                    for (String url : tsList) {
                        MultiThreads.tsExecutor.execute(() -> {
                            try {
                                tsParts.put(tsList.indexOf(url), decryptTS(httpBytes(url), video.getTsKey(), video.getTsIv()));
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            } finally {
                                tsLatch.countDown();
                                logger.debug("Downloaded ts: {}", url);
                                if (onProgress != null) {
                                    onProgress.accept(currentProgress.incrementAndGet());
                                }
                            }
                        });
                    }
                    tsLatch.await();

                    // Merge ts into mp4
                    FileOutputStream fileOutputStream = new FileOutputStream(mp4File);
                    Integer[] indexes = tsParts.keySet().toArray(Integer[]::new);
                    Arrays.sort(indexes);

                    for (int index : indexes) {
                        fileOutputStream.write(tsParts.get(index));
                    }

                    fileOutputStream.flush();
                    fileOutputStream.close();
                }

                // Download m3u8
                if (m3u8) {
                    FileUtils.writeStringToFile(m3u8File, httpString(video.getVideoURL()), StandardCharsets.UTF_8);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                if (onComplete != null) onComplete.run();
                currentTaskList.remove(video);
                // Reset progress
                if (currentTaskList.isEmpty()) {
                    currentProgress = new AtomicInteger();
                }
                System.gc();
            }
        });
    }

    /**
     * Download a list of videos.
     */
    public void download(List<M3U8Video> videoList) {
        MultiThreads.videoExecutor.execute(() -> {
            // Resolve video
            resolve(videoList);

            // Output
            File mp4Dir = new File(outputDir, "mp4s");
            File m3u8Dir = new File(outputDir, "m3u8s");

            if (split) {
                mkdirs(mp4Dir, m3u8Dir);
            }
            mkdirs(outputDir);

            videoList.forEach(this::download);
        });
    }

    private byte[] httpBytes(String url) {
        try {
            Response execute;
            execute = client.newCall(new Request.Builder().get().url(url).build()).execute();
            byte[] bytes = execute.body().bytes();
            execute.close();
            return bytes;
        } catch (IOException e) {
            logger.error("http bytes error: {}, retrying...", e.getMessage());
            return httpBytes(url);
        }
    }

    private String httpString(String url) {
        return new String(httpBytes(url));
    }

    /**
     * Method that will be called at the beginning of the download process.
     *
     * @param onBegin Given the total number of ts parts.
     */
    public void setOnBegin(IntConsumer onBegin) {
        this.onBegin = onBegin;
    }

    /**
     * Method that will be called when each ts part is downloaded.
     *
     * @param onProgress Given the number of currently downloaded ts parts.
     */
    public void setOnProgress(IntConsumer onProgress) {
        this.onProgress = onProgress;
    }

    /**
     * Method that will be called when the whole download process is finished.
     */
    public void setOnComplete(Runnable onComplete) {
        this.onComplete = onComplete;
    }

    /**
     * Specify output directory.
     */
    public void setOutputDir(File outputDir) {
        this.outputDir = outputDir;
    }

    /**
     * Whether to download m3u8 files and mp4 files into different child directories.
     */
    public void setSplit(boolean split) {
        this.split = split;
    }

    /**
     * Whether to download m3u8 files.
     */
    public void setM3u8(boolean m3u8) {
        this.m3u8 = m3u8;
    }

    /**
     * Whether to download mp4 files.
     */
    public void setMp4(boolean mp4) {
        this.mp4 = mp4;
    }
}
