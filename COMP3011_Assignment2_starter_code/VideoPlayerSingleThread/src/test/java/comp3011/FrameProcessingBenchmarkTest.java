package comp3011;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.bytedeco.javacv.Frame;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import org.junit.jupiter.api.Test;

/**
 * Compares the performance of single-threaded and multithreaded frame
 * processing.
 * Also checks that both methods produce the same results.
 * Only tests frame processing, not actual video playback.
 */
class FrameProcessingBenchmarkTest {

    // Number of frames, frame dimensions and effects used for testing.
    private static final int FRAMES = 18;
    private static final int WIDTH = 320;
    private static final int HEIGHT = 180;
    private static final List<String> EFFECTS = List.of("flicker", "yellow", "bleed");

    @Test
    void comparesSerialAndParallelEffectsAndChecksOutput() throws Exception {

        // Process one frame first to reduce the effect of initial class loading.
        processFrame(0, createProcessors());

        // Process all frames sequentially using one set of processors.
        List<byte[]> serial = new ArrayList<>();
        List<FrameProcessor> serialEffects = createProcessors();

        // Measure how long single-threaded processing takes.
        long serialStart = System.nanoTime();

        for (int number = 0; number < FRAMES; number++) {
            serial.add(processFrame(number, serialEffects));
        }

        long serialTimeNs = System.nanoTime() - serialStart;

        // Store the results from parallel processing.
        List<byte[]> parallel = new ArrayList<>();

        // Measure how long multithreaded processing takes.
        long parallelStart = System.nanoTime();

        // Create a thread pool with 2 to 4 workers depending on available CPU cores.
        try (ExecutorService pool = Executors.newFixedThreadPool(
                Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors())))) {

            // Give each worker its own set of frame processors
            // to avoid sharing processor state between threads.
            ThreadLocal<List<FrameProcessor>> effectsForThread = ThreadLocal.withInitial(this::createProcessors);

            // Store the submitted tasks so their results can be collected.
            List<Future<byte[]>> futures = new ArrayList<>();

            // Submit each frame to the thread pool for processing.
            for (int number = 0; number < FRAMES; number++) {
                int frameNumber = number;

                futures.add(pool.submit(() -> processFrame(frameNumber, effectsForThread.get())));
            }

            // Wait for all frames to finish and collect their results in order.
            for (Future<byte[]> future : futures) {
                parallel.add(future.get());
            }
        }

        long parallelTimeNs = System.nanoTime() - parallelStart;

        // Check that serial and parallel processing produce identical pixels.
        for (int i = 0; i < FRAMES; i++) {
            assertArrayEquals(serial.get(i), parallel.get(i),
                    "Parallel worker changed pixels in frame " + i);
        }

        // Convert processing times from nanoseconds to milliseconds.
        double serialMs = serialTimeNs / 1_000_000.0;
        double parallelMs = parallelTimeNs / 1_000_000.0;

        // Print processing times and frames per second for comparison.
        System.out.printf("Effect load test (%d frames, %dx%d, %s)%n",
                FRAMES, WIDTH, HEIGHT, EFFECTS);

        System.out.printf("Serial effects:   %.1f ms (%.1f frames/sec)%n",
                serialMs, FRAMES * 1000 / serialMs);

        System.out.printf("Parallel effects: %.1f ms (%.1f frames/sec)%n",
                parallelMs, FRAMES * 1000 / parallelMs);

        // No speed assertion since performance depends on the machine running the test.
    }

    // Creates and initialises a new list of frame processors.
    private List<FrameProcessor> createProcessors() {

        List<FrameProcessor> effects = new ArrayList<>();

        // Keeps track of repeated effects so each gets its own instance index.
        Map<String, Integer> counts = new HashMap<>();

        // Create a sample frame to get the required video information.
        try (Frame sample = createFrame()) {

            InfoVideo metadata = new InfoVideo("benchmark", FRAMES, WIDTH,
                    HEIGHT, sample.imageDepth, sample.imageChannels, sample.imageStride,
                    24.0, 24, 41_667, 0);

            // Create each effect and initialise it with the video information.
            for (String name : EFFECTS) {
                FrameProcessor effect = FrameProcessorFactory.create(name);

                int index = counts.getOrDefault(name, 0);
                counts.put(name, index + 1);

                effect.setAssignedInstanceIndex(index);
                effect.initialise(metadata);

                effects.add(effect);
            }

        } catch (Exception error) {
            throw new RuntimeException(error);
        }

        return effects;
    }

    // Applies all selected effects to a frame and returns its pixel data.
    private byte[] processFrame(int number, List<FrameProcessor> processors) throws Exception {

        // Create a new frame for processing.
        try (Frame frame = createFrame()) {

            // Store the frame number and its timestamp.
            InfoFrame info = new InfoFrame(number, (long) number * 41_667);

            // Apply each effect in the given order.
            for (FrameProcessor processor : processors) {
                processor.process(frame, info);
            }

            // Copy the processed pixel data into a byte array.
            ByteBuffer pixels = ((ByteBuffer) frame.image[0]).duplicate();
            pixels.clear();

            byte[] output = new byte[pixels.remaining()];
            pixels.get(output);

            return output;
        }
    }

    // Creates a test frame filled with a repeatable pixel pattern.
    private Frame createFrame() {

        Frame frame = new Frame(WIDTH, HEIGHT, Frame.DEPTH_UBYTE, 3);
        ByteBuffer pixels = (ByteBuffer) frame.image[0];

        // Fill the frame with the same pixel values each time.
        for (int i = 0; i < pixels.capacity(); i++) {
            pixels.put(i, (byte) ((i * 11) % 256));
        }

        return frame;
    }
}
