package cn.iocoder.yudao.module.campus.framework.esp32;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Esp32AssistantWebSocketHandlerTest {

    @Test
    void shouldDistinguishWakeTimeoutFromGenericCancellation() {
        assertEquals("wake_no_speech", Esp32AssistantWebSocketHandler.normalizeCancelReason("wake_no_speech"));
        assertEquals("no_speech_timeout", Esp32AssistantWebSocketHandler.normalizeCancelReason("no_speech_timeout"));
        assertEquals("capture_cancelled", Esp32AssistantWebSocketHandler.normalizeCancelReason("unexpected"));
    }

    @Test
    void silentAudioMustNotRestartPendingCommitTimer() {
        ScheduledFuture<?> pending = mock(ScheduledFuture.class);
        when(pending.isDone()).thenReturn(false);

        assertFalse(Esp32AssistantWebSocketHandler.shouldScheduleSilenceCheck(false, pending));
        assertTrue(Esp32AssistantWebSocketHandler.shouldScheduleSilenceCheck(true, pending));
        assertTrue(Esp32AssistantWebSocketHandler.shouldScheduleSilenceCheck(false, null));

        when(pending.isDone()).thenReturn(true);
        assertTrue(Esp32AssistantWebSocketHandler.shouldScheduleSilenceCheck(false, pending));
    }
}
