package cn.iocoder.yudao.module.campus.framework.esp32;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Esp32ProtocolUtilsTest {

    @Test
    void shouldWrapPcmAsMonoWav() {
        byte[] pcm = new byte[]{1, 2, 3, 4};
        byte[] wav = Esp32ProtocolUtils.pcm16LeToWav(pcm, 16000);

        assertEquals(48, wav.length);
        assertArrayEquals(new byte[]{'R', 'I', 'F', 'F'}, Arrays.copyOfRange(wav, 0, 4));
        assertArrayEquals(pcm, Arrays.copyOfRange(wav, 44, 48));
    }

    @Test
    void shouldValidateCompleteJpeg() {
        assertTrue(Esp32ProtocolUtils.isCompleteJpeg(new byte[]{
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01, (byte) 0xFF, (byte) 0xD9}));
        assertFalse(Esp32ProtocolUtils.isCompleteJpeg(new byte[]{1, 2, 3}));
    }
}
