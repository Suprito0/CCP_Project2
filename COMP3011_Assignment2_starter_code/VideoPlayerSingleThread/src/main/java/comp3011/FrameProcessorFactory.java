package comp3011;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Makes a new processor for every command-line occurrence. */
public class FrameProcessorFactory {

    public static List<FrameProcessor> createProcessors(List<String> names) {
        List<FrameProcessor> processors = new ArrayList<>();
        Map<String, Integer> counts = new HashMap<>();
        for (String name : names) {
            FrameProcessor processor = create(name);
            int occurrence = counts.getOrDefault(name, 0);
            counts.put(name, occurrence + 1); 
            processor.setAssignedInstanceIndex(occurrence); 
            processors.add(processor);
        }
        return processors;
    }

    public static FrameProcessor create(String name) {
        return switch (name) {
            case "number" -> new FrameNumberer();
            case "scratch" -> new FrameScratcher();
            case "flicker" -> new FrameFlickerer();
            case "black-and-white" -> new FrameBlackAndWhiter();
            case "yellow" -> new FrameYellower();
            case "vignette" -> new FrameVignetter();
            case "dust" -> new FrameDuster();
            case "jitter" -> new FrameJitterer();
            case "mottle" -> new FrameMottler();
            case "bleed" -> new FrameBleeder();
            case "pepper" -> new FramePepperer();
            default -> throw new IllegalArgumentException("Unknown frame processor: " + name);
        };
    }
}