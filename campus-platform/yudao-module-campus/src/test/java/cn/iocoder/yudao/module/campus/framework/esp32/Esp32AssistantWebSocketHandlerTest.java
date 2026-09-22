package cn.iocoder.yudao.module.campus.framework.esp32;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Esp32AssistantWebSocketHandlerTest {

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
