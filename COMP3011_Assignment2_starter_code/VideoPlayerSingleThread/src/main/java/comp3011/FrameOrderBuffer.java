
package comp3011;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Stores frames using their index so they can be retrieved in the correct order.
// ConcurrentHashMap allows multiple threads to access the buffer safely.
public class FrameOrderBuffer<T> {

    // Stores each value with its frame index.
    private final Map<Long, T> values = new ConcurrentHashMap<>();

    // Add a value to the buffer at the given index.
    public void put(long index, T value) {
        values.put(index, value);
    }

    // Get the value at the given index without removing it.
    public T get(long index) {
        return values.get(index);
    }

    // Get and remove the value at the given index.
    public T remove(long index) {
        return values.remove(index);
    }

    // Check if the buffer is empty.
    public boolean isEmpty() {
        return values.isEmpty();
    }

    // Remove all values from the buffer.
    public void clear() {
        values.clear();
    }
}
