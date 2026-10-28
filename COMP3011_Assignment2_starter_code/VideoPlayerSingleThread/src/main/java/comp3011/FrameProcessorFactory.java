package comp3011;

import java.util.ArrayList;
import java.util.List;

/** Makes a new processor for every command-line occurrence. */
public class FrameProcessorFactory {

    public static List<FrameProcessor> createProcessors(List<String> names) {
        List<FrameProcessor> processors = new ArrayList<>();
        for (String name : names) {
            processors.add(create(name));
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