/*
 * Starter code supplied for Adelaide University COMP3011 Assignment 2.
 * Students are free to modify this file for assessment purposes.
 * 
 * Authors:
 *   1. Simon Ratcliffe, in collaboration with GPT-5.6 Terra
 *   2. Suprito Iqbal, a1950138
 *
 * Copyright 2026 Simon Ratcliffe
 */
package comp3011;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.JavaFXFrameConverter;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;

/**
 * The model part of the video player model-view-controller architecture.
 *
 * <p>
 * Owns the FFmpeg decoder, playback clock, seeking logic, audio scheduling,
 * and frame processing. Unlike the original single-threaded version, decoding,
 * audio and frame processing run on background threads, while the JavaFX thread
 * handles playback controls and displays frames in the correct order.
 * It publishes decoded frames and playback state through callbacks supplied
 * by its owning {@link VideoPlayerController}, keeping it decoupled from the view.
 * </p>
 */
public class VideoPlayerModel {
    // Time values are stored in microseconds (us) or nanoseconds (ns).
    private static final long FIVE_SECONDS_US = 5_000_000L;
    private static final long AUDIO_LEAD_NS = 30_000_000L;
    // Limit how many frames and audio chunks can be waiting in memory.
    private static final int MAX_QUEUED_FRAMES = 12;
    private static final int MAX_AUDIO_CHUNKS = 40;

    // Effects are stored by name so each processing worker can create its own copies.
    // The handlers pass video frames and playback state back to the controller.
    private final List<String> effectNames;
    private final BiConsumer<Integer, Integer> videoSizeChangedHandler;
    private final Consumer<Image> frameReadyHandler;
    private final Consumer<String> statusChangedHandler;
    private final BiConsumer<Boolean, Boolean> playbackStateChangedHandler;
    private final Consumer<Boolean> audioOutputStateChangedHandler;

    // Playback state and timing information for the current video.
    private File videoFile;
    private PlaybackSession session;
    private boolean pauseRequested;
    private boolean audioOutputEnabled;
    private long currentTimestampUs;
    private long durationUs = -1;
    private long frameDurationUs;

    // This is the critical wiring that allows JavaFX to call our model logic
    // on each event loop heartbeat. Unlike the starter version, the timer only
    // checks completed frames and updates the GUI; decoding and effects happen
    // on background threads so they don't block the JavaFX event loop.
    // AnimationTimer always runs on the JavaFX application thread.
    private final AnimationTimer playbackTimer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            updatePlayback(now);
        }
    };

    // Stores the playback callbacks and the list of selected visual effects.
    public VideoPlayerModel(
            boolean audioEnabled,
            List<String> effectNames,
            BiConsumer<Integer, Integer> videoSizeChangedHandler,
            Consumer<Image> frameReadyHandler,
            Consumer<String> statusChangedHandler,
            BiConsumer<Boolean, Boolean> playbackStateChangedHandler,
            Consumer<Boolean> audioOutputStateChangedHandler) {
        this.audioOutputEnabled = audioEnabled;
        this.effectNames = List.copyOf(effectNames);
        this.videoSizeChangedHandler = videoSizeChangedHandler;
        this.frameReadyHandler = frameReadyHandler;
        this.statusChangedHandler = statusChangedHandler;
        this.playbackStateChangedHandler = playbackStateChangedHandler;
        this.audioOutputStateChangedHandler = audioOutputStateChangedHandler;
    }

    // Stores the selected file, clears previous timing and starts playback from
    // zero.
    public void play(File file) {
        videoFile = file;
        currentTimestampUs = 0;
        durationUs = -1;
        frameDurationUs = 0;
        startAt(0, false);
    }

    // Seeks back to the beginning of the video.
    public void startOver() {
        if (videoFile != null) {
            seekTo(0);
        }
    }

    // Moves five seconds backward from the current position.
    public void backFiveSeconds() {
        seekTo(currentTimestampUs - FIVE_SECONDS_US);
    }

    // Moves five seconds forward from the current position.
    public void forwardFiveSeconds() {
        seekTo(currentTimestampUs + FIVE_SECONDS_US);
    }

    // Pauses/resumes the playback clock, or restarts playback when finished.
    public void togglePause() {
        if (session == null) {
            if (videoFile != null) {
                long restartUs = durationUs > 0 && currentTimestampUs >= durationUs
                        ? 0 : currentTimestampUs;
                startAt(restartUs, false);
            }
            return;
        }
        pauseRequested = !pauseRequested;
        session.setPaused(pauseRequested);
        notifyPlaybackState();
    }

    // Changes the mute flag and clears audio that was already queued.
    public void toggleAudioOutput() {
        audioOutputEnabled = !audioOutputEnabled;
        if (session != null) {
            session.audioEnabled = audioOutputEnabled;
            if (!audioOutputEnabled) {
                session.audioQueue.clear();
            }
        }
        audioOutputStateChangedHandler.accept(audioOutputEnabled);
    }

    // Returns the current audio-output setting.
    public boolean isAudioOutputEnabled() {
        return audioOutputEnabled;
    }

    // Closes playback and clears the displayed frame.
    public void stopPlayback() {
        stopSession();
        pauseRequested = false;
        currentTimestampUs = 0;
        statusChangedHandler.accept("Stopped");
        frameReadyHandler.accept(null);
        notifyPlaybackState();
    }

    // Stops background workers and releases playback resources when the app closes.
    public void shutdown() {
        stopSession();
    }

    // Calculates a valid seek position and restarts the playback session there.
    private void seekTo(long targetUs) {
        if (videoFile == null) {
            return;
        }
        long positionUs = Math.max(0, targetUs);
        if (durationUs > 0) {
            // FFmpeg usually cannot decode from exactly the last microsecond.
            positionUs = Math.min(positionUs, Math.max(0, durationUs - Math.max(1, frameDurationUs)));
        }
        currentTimestampUs = positionUs;
        startAt(positionUs, pauseRequested);
    }

    // Reopens playback at the given time and starts the JavaFX timer and decoder.
    private void startAt(long timestampUs, boolean startPaused) {
        stopSession();
        pauseRequested = startPaused;

        PlaybackSession next = new PlaybackSession(timestampUs, audioOutputEnabled, startPaused);
        session = next;
        statusChangedHandler.accept(videoFile.getName());
        playbackTimer.start();
        next.start();
        notifyPlaybackState();
    }

    // Stops the GUI timer and cancels the current background playback work.
    private void stopSession() {
        playbackTimer.stop();
        PlaybackSession previous = session;
        session = null;
        if (previous != null) {
            previous.cancel();
        }
    }

    // Uses a callback to update the play/pause controls.
    private void notifyPlaybackState() {
        playbackStateChangedHandler.accept(session != null, pauseRequested);
    }

    // Runs once per JavaFX timer tick. The GUI thread checks for errors,
    // displays the next frame when its playback time arrives, and checks for
    // the end of the video. It does not decode or process video frames.
    private void updatePlayback(long now) {
        PlaybackSession active = session;
        if (active == null) {
            return;
        }
        if (active.failure != null) {
            Exception error = active.failure;
            error.printStackTrace();
            stopSession();
            statusChangedHandler.accept("Error: " + error.getMessage());
            notifyPlaybackState();
            return;
        }

        // A worker can complete frame 2 before frame 1. Never display out of order.
        // Get only the next expected frame, even if later frames are already ready.
        ReadyFrame ready = active.readyFrames.get(active.nextDisplayIndex);
        if (ready != null) {
            if (active.clockStartNs == 0) {
                active.clockStartNs = now;
                if (pauseRequested) {
                    active.pausedAtNs = now;
                }
            }

            // Calculate when this frame should appear using the playback clock.
            long elapsedUs = Math.max(0, ready.timestampUs - active.firstVideoTimestampUs);
            long targetNs = active.clockStartNs + elapsedUs * 1_000L;

            // Show one frame when seeking to a paused position, then freeze.
            boolean showWhilePaused = pauseRequested && active.nextDisplayIndex == 0;
            if ((showWhilePaused || !pauseRequested) && (showWhilePaused || now >= targetNs)) {
                active.readyFrames.remove(active.nextDisplayIndex);
                active.nextDisplayIndex++;
                // Make space for the decoder to queue another processed frame.
                active.frameSlots.release();

                currentTimestampUs = ready.timestampUs;
                frameReadyHandler.accept(ready.image);

                // Dump some logging to the console once per second so that
                // real-time performance can be monitored.
                if (ready.frameNumber % Math.max(1, active.intFrameRate) == 0) {
                    long headroomUs = (targetNs - ready.completedAtNs) / 1_000L;
                    System.out.printf("Frame headroom is %dus out of %dus spare.%n",
                            headroomUs, active.frameDurationUs);
                }
            }
        }

        // Once decoding and frame processing are done and all frames are shown,
        // update the state and release the remaining playback resources.
        if (active.decoderFinished && active.nextDisplayIndex == active.frameCount
                && active.readyFrames.isEmpty()) {
            currentTimestampUs = durationUs > 0 ? durationUs : currentTimestampUs;
            stopSession();
            pauseRequested = true;
            statusChangedHandler.accept("Playback finished");
            notifyPlaybackState();
        }
    }

    // Keeps the threads, queues, frame buffers and playback clock for one
    // playback session. Seeking creates a new session to replace the old one.
    private class PlaybackSession {
        // A semaphore limits queued frames, and the frame order buffer stores
        // completed frames by sequence number for the JavaFX thread.
        private final long startTimestampUs;
        private final Semaphore frameSlots = new Semaphore(MAX_QUEUED_FRAMES);
        private final FrameOrderBuffer<ReadyFrame> readyFrames = new FrameOrderBuffer<>();
        private final BlockingQueue<AudioChunk> audioQueue = new ArrayBlockingQueue<>(MAX_AUDIO_CHUNKS);
        private final ExecutorService processingPool;
        private final Thread decoderThread;
        private Thread audioThread;
        private AudioPlayer audioPlayer;

        // These values are shared between the decoder, audio and GUI threads.
        // volatile makes state changes visible between the threads.
        private volatile boolean cancelled;
        private volatile boolean decoderFinished;
        private volatile boolean paused;
        private volatile boolean audioEnabled;
        private volatile Exception failure;
        private volatile long clockStartNs;
        private volatile long pausedAtNs;
        private volatile long firstVideoTimestampUs = -1;
        private volatile long frameDurationUs;
        private volatile int intFrameRate = 1;
        private volatile long frameCount;
        private long nextDisplayIndex; // Only accessed on the JavaFX thread.

        // Sets up the decoder and a separate worker for frame processing.
        private PlaybackSession(long timestampUs, boolean audioEnabled, boolean paused) {
            this.startTimestampUs = timestampUs;
            this.audioEnabled = audioEnabled;
            this.paused = paused;


            // Use multiple cores for expensive frame effects while keeping the GUI free.
            int cores = Runtime.getRuntime().availableProcessors();
            int workerCount = Math.max(2, Math.min(4, cores - 1)); 
            processingPool = Executors.newFixedThreadPool(workerCount, task -> {
                Thread thread = new Thread(task, "frame-processor");
                thread.setDaemon(true);
                return thread;
            });
            decoderThread = new Thread(this::decode, "video-decoder");
            decoderThread.setDaemon(true);
        }

        // Starts decoding on a separate thread.
        private void start() {
            decoderThread.start();
        }

        // Pauses/resumes playback and adjusts the clock to account for time paused.
        private void setPaused(boolean value) {
            if (value == paused) {
                return;
            }
            paused = value;
            long now = System.nanoTime();
            if (value) {
                pausedAtNs = now;
            } else if (pausedAtNs != 0 && clockStartNs != 0) {
                clockStartNs += now - pausedAtNs;
                pausedAtNs = 0;
            }
        }

        // Signals the background threads to stop and clears any buffered work.
        private void cancel() {
            cancelled = true;
            decoderThread.interrupt();
            if (audioThread != null) {
                audioThread.interrupt();
            }
            processingPool.shutdownNow();
            audioQueue.clear();
            readyFrames.clear();
            frameSlots.release(MAX_QUEUED_FRAMES);
        }

        // Reads video and audio frames, queues audio, and sends video frames to
        // the processing worker. The decoder thread owns the FFmpeg grabber.
        private void decode() {
            // FFmpegFrameGrabber is owned by the decoder thread only.
            try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoFile)) {
                grabber.setImageMode(FrameGrabber.ImageMode.COLOR);
                grabber.setSampleMode(FrameGrabber.SampleMode.SHORT);
                grabber.start();

                // Read the frame rate, duration, dimensions and audio details.
                double rate = grabber.getFrameRate();
                intFrameRate = Math.max(1, (int) Math.round(rate));
                frameDurationUs = rate > 0 ? (long) (1_000_000.0 / rate) : 41_667;
                int videoFrames = grabber.getLengthInVideoFrames();
                long length = grabber.getLengthInTime();
                boolean hasAudio = grabber.hasAudio();

                int videoWidth = grabber.getImageWidth();
                int videoHeight = grabber.getImageHeight();
                // Update the controller on the JavaFX thread, not the decoder thread.
                Platform.runLater(() -> {
                    if (session == this) {
                        videoSizeChangedHandler.accept(videoWidth, videoHeight);
                        durationUs = length;
                        VideoPlayerModel.this.frameDurationUs = frameDurationUs;
                    }
                });

                // Open the audio device and start a separate audio output thread.
                if (hasAudio) {
                    audioPlayer = new AudioPlayer();
                    audioPlayer.open(grabber.getSampleRate(), grabber.getAudioChannels());
                    audioThread = new Thread(this::playAudio, "audio-output");
                    audioThread.setDaemon(true);
                    audioThread.start();
                }
                if (startTimestampUs > 0) {
                    grabber.setTimestamp(startTimestampUs);
                }

                // Initialise effect metadata when the first video frame arrives.
                // Each worker keeps its own processor instances.
                InfoVideo videoInfo = null;
                ThreadLocal<List<FrameProcessor>> workerProcessors = new ThreadLocal<>();
                long nextSequence = 0;

                // Read frames until the video ends or this session is cancelled.
                while (!cancelled) {
                    Frame frame = hasAudio ? grabber.grab() : grabber.grabImage();
                    if (frame == null) {
                        break;
                    }
                    long timestampUs = grabber.getTimestamp();

                    // Copy audio samples into a bounded queue for the audio thread.
                    if (hasAudio && frame.samples != null && audioEnabled) {
                        byte[] samples = audioPlayer.copySamples(frame);
                        if (samples.length > 0) {
                            audioQueue.put(new AudioChunk(timestampUs, samples));
                        }
                    }
                    // Audio-only frames have no image to process.
                    if (frame.image == null) {
                        continue;
                    }

                    if (videoInfo == null) {
                        firstVideoTimestampUs = timestampUs;
                        videoInfo = new InfoVideo(
                                mediaName(videoFile), videoFrames, frame.imageWidth,
                                frame.imageHeight, frame.imageDepth, frame.imageChannels,
                                frame.imageStride, rate, intFrameRate, (int) frameDurationUs,
                                grabber.getPixelFormat());
                    }

                    // The semaphore prevents an unlimited number of full-resolution frames
                    // from being copied into memory when processing/display falls behind.
                    frameSlots.acquire();
                    if (cancelled) {
                        frameSlots.release();
                        break;
                    }

                    Frame ownedFrame = frame.clone(); // Grabber reuses its frame buffers.
                    // Keep each frame's metadata and original decoding order.
                    InfoVideo metadata = videoInfo;
                    InfoFrame info = new InfoFrame(grabber.getFrameNumber(), timestampUs);
                    long sequence = nextSequence++;
                    frameCount = nextSequence;

                    // Process the copied frame on a background worker.
                    processingPool.submit(() -> processOneFrame(
                            ownedFrame, info, metadata, sequence, workerProcessors));
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (Exception error) {
                if (!cancelled) {
                    failure = error;
                }
            } finally {
                // Wait for queued frames to finish before marking decoding complete.
                processingPool.shutdown();
                try {
                    while (!processingPool.awaitTermination(100, TimeUnit.MILLISECONDS)) {
                        if (cancelled) {
                            processingPool.shutdownNow();
                            break;
                        }
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    processingPool.shutdownNow();
                }
                decoderFinished = true;
            }
        }

        // Applies the configured visual effects and prepares an image for the GUI.
        private void processOneFrame(Frame frame, InfoFrame info, InfoVideo metadata,
                long sequence, ThreadLocal<List<FrameProcessor>> workerProcessors) {
            try {
                if (cancelled) {
                    return;
                }
                List<FrameProcessor> processors = workerProcessors.get();
                if (processors == null) {
                    processors = makeWorkerProcessors(metadata);
                    workerProcessors.set(processors);
                }

                // Each worker owns its processors, so no mutable effect state is shared.
                // Effect order matters: each processor changes the frame for the next one.
                for (FrameProcessor processor : processors) {
                    processor.process(frame, info);
                }

                // The converter may reuse an image internally, so take a deep copy.
                // JavaFX only receives completed images, not live mutable frame buffers.
                // Convert JavaCV pixels to a JavaFX Image before presenting it to
                // the view. Copying it also avoids displaying reused frame buffers.
                Image image;
                try (JavaFXFrameConverter converter = new JavaFXFrameConverter()) {
                    Image converted = converter.convert(frame);
                    image = new WritableImage(converted.getPixelReader(),
                            (int) converted.getWidth(), (int) converted.getHeight());
                }

                // Store the finished frame under its decoding index so the GUI can
                // display it in the original order.
                if (!cancelled) {
                    readyFrames.put(sequence, new ReadyFrame(image, info.frameNumber(),
                            info.timestampUs(), System.nanoTime()));
                    return; // The GUI releases the frame slot after displaying it.
                }
            } catch (Exception error) {
                if (!cancelled) {
                    failure = error;
                }
            } finally {
                frame.close();
            }
            // Errors and cancelled jobs must return their slot to the pool.
            frameSlots.release();
        }

        // Creates and initialises this worker's visual effects in command-line
        // order, keeping repeated effects as separate instances.
        private List<FrameProcessor> makeWorkerProcessors(InfoVideo metadata) throws Exception {
            List<FrameProcessor> processors = new ArrayList<>();
            Map<String, Integer> counts = new HashMap<>();
            for (String name : effectNames) {
                FrameProcessor processor = FrameProcessorFactory.create(name);
                int effectNumber = counts.getOrDefault(name, 0);
                counts.put(name, effectNumber + 1);
                processor.setAssignedInstanceIndex(effectNumber);
                processor.initialise(metadata);
                processors.add(processor);
            }
            return processors;
        }

        // Sends queued audio chunks to the output device when their playback time
        // arrives, using a small lead time to keep audio in sync with video.
        private void playAudio() {
            AudioChunk playing = null;
            boolean wasInactive = false;
            try {
                while (!cancelled && (!decoderFinished || playing != null || !audioQueue.isEmpty())) {
                    // Flush output when paused or muted instead of playing stale audio.
                    if (paused || !audioEnabled) {
                        if (!wasInactive) {
                            audioPlayer.flush();
                            wasInactive = true;
                        }
                        if (!audioEnabled) {
                            playing = null;
                            audioQueue.clear();
                        }
                        Thread.sleep(5);
                        continue;
                    }
                    wasInactive = false;
                    if (clockStartNs == 0 || firstVideoTimestampUs < 0) {
                        Thread.sleep(5);
                        continue;
                    }
                    if (playing == null) {
                        playing = audioQueue.poll(10, TimeUnit.MILLISECONDS);
                        continue;
                    }

                    // Calculate when this audio chunk should be played.
                    long audioDelayUs = Math.max(0, playing.timestampUs - firstVideoTimestampUs);
                    long dueNs = clockStartNs + audioDelayUs * 1_000L;
                    if (System.nanoTime() + AUDIO_LEAD_NS < dueNs) {
                        Thread.sleep(2);
                        continue;
                    }

                    int written = audioPlayer.write(playing.samples, playing.offset,
                            playing.samples.length - playing.offset);
                    if (written <= 0) {
                        Thread.sleep(2);
                    } else {
                        playing.offset += written;
                        if (playing.offset >= playing.samples.length) {
                            playing = null;
                        }
                    }
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                if (audioPlayer != null) {
                    audioPlayer.close();
                }
            }
        }
    }

    // Removes the filename extension to make a stable identifier for effects.
    private String mediaName(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }

    // Groups a ready image with frame and timing metadata.
    private record ReadyFrame(Image image, int frameNumber, long timestampUs,
            long completedAtNs) {
    }

    // Holds one audio chunk while keeping track of partial writes to the sound
    // device.
    private static class AudioChunk {
        private final long timestampUs;
        private final byte[] samples;
        private int offset;

        // Stores the timestamp and bytes for one queued audio packet.
        private AudioChunk(long timestampUs, byte[] samples) {
            this.timestampUs = timestampUs;
            this.samples = samples;
        }
    }
}