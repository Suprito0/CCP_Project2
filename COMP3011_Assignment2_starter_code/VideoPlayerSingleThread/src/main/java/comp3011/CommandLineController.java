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
import java.util.List;

/**
 * Created in the VideoPlayerApp's main function to receive and parse the video player's command-line arguments.
 *
 * <p>
 * Extracts the video file and supported launch options, reports invalid input
 * or help text, and exposes the resulting launch configuration via methods to the
 * {@link VideoPlayerApp}.
 * </p>
 */
public class CommandLineController {

     // Stores the selected effects in command-line order.
    private final List<String> effectNames = new ArrayList<>();

    // Settings determined by the command-line arguments.
    private boolean helpRequested;
    private boolean audioRequested;
    private boolean maximiseRequested;
    private Integer displayId;
    private File videoFile;
    private String errorMessage;

    public CommandLineController(String[] args) {
        // Parse the arguments and report any errors or help requests.
        parse(args);
        if (errorMessage != null) {
            System.out.println(errorMessage);
        }
        if (helpRequested) {
            printHelp();
        }
    }

    // Returns a copy of the selected effects so the original list cannot be modified.
    public List<String> getEffectNames() {
        return List.copyOf(effectNames);
    }
    
    public File getVideoFile() {
        return videoFile;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getExitCode() {
        return errorMessage == null ? 0 : 1;
    }

    public Integer getDisplayId() {
        return displayId;
    }

    public boolean isAudioRequested() {
        return audioRequested;
    }

    public boolean isMaximiseRequested() {
        return maximiseRequested;
    }

    // Only launches the player if a valid video was provided and help was not requested.
    public boolean shouldLaunchApplication() {
        return errorMessage == null && videoFile != null && !helpRequested;
    }

    // Records the first error encountered without overwriting it with later errors.
    private void setError(String message) {
        if (errorMessage == null) {
            errorMessage = message;
        }
    }

    // Handles long-form options such as --audio and --flicker-frames.
    private void parseLongOption(String option) {
        switch (option) {
            case "--help" -> helpRequested = true;
            case "--audio" -> audioRequested = true;
            case "--maximise" -> maximiseRequested = true;
            case "--monitor-1" -> setDisplayId(1);
            case "--monitor-2" -> setDisplayId(2);
            case "--number-frames" -> effectNames.add("number");
            case "--scratch-frames" -> effectNames.add("scratch");
            case "--flicker-frames" -> effectNames.add("flicker");
            case "--black-and-white" -> effectNames.add("black-and-white");
            case "--yellow-frames" -> effectNames.add("yellow");
            case "--vignette-frames" -> effectNames.add("vignette");
            case "--dust-frames" -> effectNames.add("dust");
            case "--jitter-frames" -> effectNames.add("jitter");
            case "--mottle-frames" -> effectNames.add("mottle");
            case "--bleed-frames" -> effectNames.add("bleed");
            case "--pepper-frames" -> effectNames.add("pepper");
            default -> setError("Unknown option: " + option);
        }
    }

    // Handles short options such as -a, -f and combined options like -nfd.
    private void parseShortOptions(String letters) {
        // Process each character separately to support stacked short options.
        for (char letter : letters.toCharArray()) {
            switch (letter) {
                case 'h' -> helpRequested = true;
                case 'a' -> audioRequested = true;
                case 'x' -> maximiseRequested = true;
                case '1' -> setDisplayId(1);
                case '2' -> setDisplayId(2);
                case 'n' -> effectNames.add("number");
                case 's' -> effectNames.add("scratch");
                case 'f' -> effectNames.add("flicker");
                case 'w' -> effectNames.add("black-and-white");
                case 'y' -> effectNames.add("yellow");
                case 'v' -> effectNames.add("vignette");
                case 'd' -> effectNames.add("dust");
                case 'j' -> effectNames.add("jitter");
                case 'm' -> effectNames.add("mottle");
                case 'b' -> effectNames.add("bleed");
                case 'P' -> effectNames.add("pepper");
                default -> setError("Unknown option: -" + letter);
            }
        }
    }

    // Goes through all command-line arguments and separates options from the video filename.
    private void parse(String[] args) {
        List<String> fileNames = new ArrayList<>();
        for (String arg : args) {
            // long-form options.
            if (arg.startsWith("--")) {
                parseLongOption(arg);
            // short options
            } else if (arg.startsWith("-") && arg.length() > 1) {
                parseShortOptions(arg.substring(1));
            // Anything else is treated as a video filename.
            } else {
                fileNames.add(arg);
            }
        }

        // The player only accepts one video file at a time.
        if (fileNames.size() > 1) {
            setError("Only one video file can be provided.");
        } else if (fileNames.size() == 1) {
            // Check whether the supplied video file actually exists.
            videoFile = new File(fileNames.get(0));
            if (!videoFile.isFile()) {
                setError("File not found: " + videoFile.getPath());
                videoFile = null;
            }
            // A video file is required unless the user only wants help.
        } else if (!helpRequested) {
            setError("No video file specified.");
        }
    }

    // Sets the selected monitor and prevents conflicting monitor options.
    private void setDisplayId(int displayId) {
        if (this.displayId != null && this.displayId != displayId) {
            errorMessage = "Only one display option can be used";
            return;
        }
        this.displayId = displayId;
    }

    // Prints the available command-line options and their descriptions.
    private void printHelp() {
        System.out.println("Usage: VideoPlayer [options] [video-file]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -h, --help         Show this help message");
        System.out.println("  -a, --audio        Play audio");
        System.out.println("  -x, --maximise     Open the player maximised");
        System.out.println("  -1, --monitor-1    Open the player on display 1");
        System.out.println("  -2, --monitor-2    Open the player on display 2");
        System.out.println();
        System.out.println("Frame processors:");
        System.out.println("  -n, --number-frames      Render the frame number onto each frame");
        System.out.println("  -s, --scratch-frames     Render vertical film scratches");
        System.out.println("  -f, --flicker-frames     Randomly dim frames");
        System.out.println("  -w, --black-and-white    Convert frames to black and white");
        System.out.println("  -y, --yellow-frames      Apply a warmer colour temperature");
        System.out.println("  -v, --vignette-frames    Darken the frame edges");
        System.out.println("  -d, --dust-frames        Render dust and hair marks");
        System.out.println("  -j, --jitter-frames      Randomly displace frames by a few pixels");
        System.out.println("  -m, --mottle-frames      Add cloudy emulsion mottling");
        System.out.println("  -b, --bleed-frames       Bleed light into frames");
        System.out.println("  -P, --pepper-frames      Pepper frames with dark spots/blotches");
        System.out.println();
        System.out.println("Frame processors are applied in command-line order and may be repeated.");
        System.out.println("Example: -nssnfwyvdjmbp numbers, scratches twice, numbers again, flickers,");
        System.out.println("converts, warms, vignettes, dusts, jitters, mottles, bleeds, then peppers.");
    }
}
