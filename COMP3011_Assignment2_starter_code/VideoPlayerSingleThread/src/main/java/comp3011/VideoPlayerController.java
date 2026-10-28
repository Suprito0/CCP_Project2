/*
 * Starter code supplied for Adelaide University COMP3011 Assignment 2.
 * Students are free to modify this file for assessment purposes.
 * 
 * Authors:
 *   1. Simon Ratcliffe, in collaboration with GPT-5.6 Terra
 *   2. <student name and student number insert here upon modification>
 *
 * Copyright 2026 Simon Ratcliffe
 */
package comp3011;

import java.io.File;
import java.util.function.BiConsumer;

import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/**
 * The controller part of the video player model-view-controller architecture.
 * 
 * <p>
 * Wires up events from the {@link VideoPlayerView} to the {@link VideoPlayerModel}.
 * This includes button actions, keyboard shortcuts and display resizing.
 * </p>
 */
public class VideoPlayerController {
    // The model handles playback; the view handles display and buttons.
    private final VideoPlayerModel model;
    private final VideoPlayerView view;
    private final BiConsumer<Integer, Integer> videoSizeChangedHandler;

    // Creates the view and model, then connects buttons and callbacks.
    public VideoPlayerController(
            boolean audioEnabled,
            BiConsumer<Integer, Integer> videoSizeChangedHandler) {
        this.view = new VideoPlayerView();
        this.videoSizeChangedHandler = videoSizeChangedHandler;
        model = new VideoPlayerModel(
                audioEnabled,
                this::onVideoSizeChanged,
                this::onFrameReady,
                this::onStatusChanged,
                this::onPlaybackStateChanged,
                this::onAudioOutputStateChanged);

        // Connect each button to the corresponding controller method.
        view.getStartOverButton().setOnAction(_ -> startOver());
        view.getBackFiveButton().setOnAction(_ -> backFiveSeconds());
        view.getPausePlayButton().setOnAction(_ -> togglePause());
        view.getForwardFiveButton().setOnAction(_ -> forwardFiveSeconds());
        view.getStopButton().setOnAction(_ -> stopAndExit());
        view.getAudioButton().setOnAction(_ -> toggleAudioOutput());
        view.showAudioEnabled(model.isAudioOutputEnabled());
    }

    // Connects keys to actions such as pause, seek, stop and mute.
    public void installKeyboardShortcuts(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                stopAndExit();
                event.consume();
            } else if (event.getCode() == KeyCode.SPACE) {
                togglePause();
                event.consume();
            } else if (event.getCode() == KeyCode.LEFT) {
                backFiveSeconds();
                event.consume();
            } else if (event.getCode() == KeyCode.RIGHT) {
                forwardFiveSeconds();
                event.consume();
            } else if (event.getCode() == KeyCode.HOME) {
                startOver();
                event.consume();
            } else if (event.getCode() == KeyCode.A) {
                toggleAudioOutput();
                event.consume();
            }
        });
    }

    // Gives VideoPlayerApp access to the visual layout.
    public VideoPlayerView getView() {
        return view;
    }

    // Enables the controls and tells the model to open the video.
    public void play(File file) {
        view.setPlaybackControlsDisabled(false);
        model.play(file);
    }

    // Asks the model to release its resources.
    public void shutdown() {
        model.shutdown();
    }

    // Requests a seek back to the beginning.
    private void startOver() {
        model.startOver();
    }

    // Requests a five-second rewind.
    private void backFiveSeconds() {
        model.backFiveSeconds();
    }

    // Switches between paused and playing states.
    private void togglePause() {
        model.togglePause();
    }

    // Requests a five-second skip forward.
    private void forwardFiveSeconds() {
        model.forwardFiveSeconds();
    }

    // Switches audio playback on or off.
    private void toggleAudioOutput() {
        model.toggleAudioOutput();
    }

    // Stops playback and closes the JavaFX application.
    private void stopAndExit() {
        model.stopPlayback();
        javafx.application.Platform.exit();
    }

    // Passes the decoded video size back to the window.
    private void onVideoSizeChanged(int width, int height) {
        videoSizeChangedHandler.accept(width, height);
    }

    // Displays an image supplied by the model.
    private void onFrameReady(Image image) {
        view.setImage(image);
    }

    // Updates the status text shown in the controls.
    private void onStatusChanged(String status) {
        view.setStatus(status);
    }

    // Changes the play/pause icon to match the current state.
    private void onPlaybackStateChanged(boolean playing, boolean paused) {
        if (playing && !paused) {
            view.showPauseAction();
        } else {
            view.showPlayAction();
        }
    }

    // Updates the audio icon to match the mute setting.
    private void onAudioOutputStateChanged(boolean audioOutputEnabled) {
        view.showAudioEnabled(audioOutputEnabled);
    }
}