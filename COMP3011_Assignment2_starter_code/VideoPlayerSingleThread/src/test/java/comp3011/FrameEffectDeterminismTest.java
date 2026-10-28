package comp3011; 

import java.nio.ByteBuffer;
import java.util.List; 

import org.bytedeco.javacv.Frame;
import static org.junit.jupiter.api.Assertions.assertArrayEquals; 
import org.junit.jupiter.api.Test; 

/** Checks that independent worker effect instances produce the same pixels. */ 
class FrameEffectDeterminismTest { 

    @Test 
    void seededEffectsGiveSamePixelsAcrossWorkers() throws Exception { 
        List<String> names = List.of("flicker", "jitter", "mottle", "dust", "pepper", "scratch"); 
        for (String name : names) { 
            try (Frame one = makeFrame(); Frame two = makeFrame()) { 
                InfoVideo details = new InfoVideo("sample", 200, one.imageWidth, 
                        one.imageHeight, one.imageDepth, one.imageChannels, 
                        one.imageStride, 24.0, 24, 41_667, 0); 
                FrameProcessor first = FrameProcessorFactory.create(name); 
                FrameProcessor second = FrameProcessorFactory.create(name); 
                first.setAssignedInstanceIndex(0); 
                second.setAssignedInstanceIndex(0); 
                first.initialise(details); 
                second.initialise(details); 
                first.process(one, new InfoFrame(30, 1_250_000)); 
                second.process(two, new InfoFrame(30, 1_250_000)); 
                assertArrayEquals(bytes(one), bytes(two), "Effect should be repeatable: " + name); 
            } 
        } 
    } 

    private Frame makeFrame() { 
        Frame frame = new Frame(80, 64, Frame.DEPTH_UBYTE, 3); 
        ByteBuffer pixels = (ByteBuffer) frame.image[0]; 
        for (int i = 0; i < pixels.capacity(); i++) { 
            pixels.put(i, (byte) (i % 251)); 
        } 
        return frame; 
    } 

    private byte[] bytes(Frame frame) { 
        ByteBuffer pixels = ((ByteBuffer) frame.image[0]).duplicate(); 
        pixels.clear(); 
        byte[] result = new byte[pixels.remaining()]; 
        pixels.get(result); 
        return result; 
    } 
} 