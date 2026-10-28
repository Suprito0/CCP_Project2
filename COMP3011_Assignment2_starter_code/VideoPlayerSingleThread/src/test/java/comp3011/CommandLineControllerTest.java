
package comp3011;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

class CommandLineControllerTest {

    // Create a temporary video file for testing.
    // This avoids needing an actual video file in the project.
    private File video() throws IOException {
        File file = File.createTempFile("player-test", ".mp4");
        file.deleteOnExit();
        return file;
    }

    @Test
    void combinedShortOptionsKeepOrderAndDuplicates() throws IOException {

        // Test multiple short options combined into one argument.
        CommandLineController cli = new CommandLineController(
                new String[] {"-jfddn", video().getAbsolutePath()});

        // Check that valid arguments allow the application to launch.
        assertTrue(cli.shouldLaunchApplication());

        // Check that effects are stored in the correct order,
        // including repeated effects such as dust.
        assertEquals(
                List.of("jitter", "flicker", "dust", "dust", "number"),
                cli.getEffectNames()
        );
    }

    @Test
    void longAndShortOptionsCanBeMixed() throws IOException {

        // Test using long and short options in the same command.
        CommandLineController cli = new CommandLineController(
                new String[] {
                        "--dust-frames",
                        "-j",
                        "-d",
                        "--number-frames",
                        video().getAbsolutePath()
                });

        // Check that all effects are recognised in the given order.
        assertEquals(
                List.of("dust", "jitter", "dust", "number"),
                cli.getEffectNames()
        );
    }

    @Test
    void unknownOptionsAndMissingFilesAreRejected() {

        // An unknown long option should return an error exit code.
        assertEquals(
                1,
                new CommandLineController(
                        new String[] {"--unknown"}
                ).getExitCode()
        );

        // An unknown short option should prevent the application
        // from launching, even when a file argument is provided.
        assertFalse(
                new CommandLineController(
                        new String[] {"-z", "fake.mp4"}
                ).shouldLaunchApplication()
        );
    }

    @Test
    void helpDoesNotStartJavaFx() {

        // Request the help message without launching the player.
        CommandLineController cli = new CommandLineController(
                new String[] {"--help"});

        // Help should exit successfully.
        assertEquals(0, cli.getExitCode());

        // The JavaFX application should not start.
        assertFalse(cli.shouldLaunchApplication());
    }

    @Test
    void allLongEffectOptionsCreateTheExpectedNames() throws IOException {

        // Test every supported long-form frame processing option.
        CommandLineController cli = new CommandLineController(
                new String[] {
                        "--number-frames",
                        "--scratch-frames",
                        "--flicker-frames",
                        "--black-and-white",
                        "--yellow-frames",
                        "--vignette-frames",
                        "--dust-frames",
                        "--jitter-frames",
                        "--mottle-frames",
                        "--bleed-frames",
                        "--pepper-frames",
                        video().getAbsolutePath()
                });

        // Verify that each option maps to the correct effect name.
        assertEquals(
                List.of(
                        "number", "scratch", "flicker",
                        "black-and-white", "yellow", "vignette",
                        "dust", "jitter", "mottle", "bleed", "pepper"
                ),
                cli.getEffectNames()
        );
    }

    @Test
    void contradictoryMonitorsAndTwoVideosAreRejected() throws IOException {

        // Selecting two different monitors should return an error.
        assertEquals(
                1,
                new CommandLineController(
                        new String[] {
                                "-1", "-2", video().getAbsolutePath()
                        }
                ).getExitCode()
        );

        // Providing two video file arguments should also return an error.
        assertEquals(
                1,
                new CommandLineController(
                        new String[] {
                                video().getAbsolutePath(),
                                video().getAbsolutePath()
                        }
                ).getExitCode()
        );
    }
}
