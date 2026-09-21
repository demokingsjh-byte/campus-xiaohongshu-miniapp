package cn.iocoder.yudao.module.campus.framework.esp32;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * ESP32 媒体协议和 WAV 封装工具。
 */
public final class Esp32ProtocolUtils {

    public static final byte AUDIO_FRAME = 0x01;
    public static final byte IMAGE_FRAME = 0x02;
    public static final int INPUT_SAMPLE_RATE = 16000;
    public static final int OUTPUT_SAMPLE_RATE = 24000;
    public static final int MIN_AUDIO_BYTES = INPUT_SAMPLE_RATE * 2 / 4;

    private Esp32ProtocolUtils() {
    }

    public static byte[] pcm16LeToWav(byte[] pcm, int sampleRate) {
        int dataLength = pcm.length;
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put(new byte[]{'R', 'I', 'F', 'F'});
        header.putInt(36 + dataLength);
        header.put(new byte[]{'W', 'A', 'V', 'E'});
        header.put(new byte[]{'f', 'm', 't', ' '});
        header.putInt(16);
        header.putShort((short) 1);
        header.putShort((short) 1);
        header.putInt(sampleRate);
        header.putInt(sampleRate * 2);
        header.putShort((short) 2);
        header.putShort((short) 16);
        header.put(new byte[]{'d', 'a', 't', 'a'});
        header.putInt(dataLength);
        ByteArrayOutputStream output = new ByteArrayOutputStream(44 + dataLength);
        try {
            output.write(header.array());
            output.write(pcm);
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
        return output.toByteArray();
    }

    public static boolean isCompleteJpeg(byte[] image) {
        return image != null
                && image.length >= 5
                && image[0] == (byte) 0xFF
                && image[1] == (byte) 0xD8
                && image[2] == (byte) 0xFF
                && image[image.length - 2] == (byte) 0xFF
                && image[image.length - 1] == (byte) 0xD9;
    }
}
