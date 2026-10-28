
package comp3011;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class FrameOrderBufferTest {

    @Test
    void doesNotSkipAFrameThatFinishesLate() {

        // Create a buffer to store frames using their sequence numbers.
        FrameOrderBuffer<String> buffer = new FrameOrderBuffer<>();

        // Add frame 2 first to simulate frames finishing out of order.
        buffer.put(2, "third");

        // Frame 0 should not be available yet.
        assertNull(buffer.get(0));

        // Add and retrieve frame 0.
        buffer.put(0, "first");
        assertEquals("first", buffer.remove(0));

        // Frame 1 is still missing.
        assertNull(buffer.get(1));

        // Add frame 1 and retrieve the remaining frames in order.
        buffer.put(1, "second");
        assertEquals("second", buffer.remove(1));
        assertEquals("third", buffer.remove(2));

        // Check that all frames have been removed.
        assertTrue(buffer.isEmpty());
    }

    @Test
    void acceptsConcurrentWorkerCompletions() throws Exception {

        // Create a buffer shared between multiple worker threads.
        FrameOrderBuffer<Long> buffer = new FrameOrderBuffer<>();

        // Create a thread pool with 4 workers.
        try (ExecutorService workers = Executors.newFixedThreadPool(4)) {

            // Store the submitted tasks so we can wait for them to finish.
            List<Future<?>> jobs = new ArrayList<>();

            // Submit 100 frames in reverse order to simulate
            // frames being processed by different workers.
            for (long i = 99; i >= 0; i--) {
                long frame = i;
                jobs.add(workers.submit(() -> buffer.put(frame, frame)));
            }

            // Wait until all worker tasks have finished.
            for (Future<?> job : jobs) {
                job.get();
            }
        }

        // Check that every frame can be retrieved in the correct order.
        for (long index = 0; index < 100; index++) {
            assertEquals(Long.valueOf(index), buffer.remove(index));
        }

        // Check that the buffer is empty after removing all frames.
        assertTrue(buffer.isEmpty());
    }
}
