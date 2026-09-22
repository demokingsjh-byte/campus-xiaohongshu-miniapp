package cn.iocoder.yudao.module.campus.service.esp32;

import cn.iocoder.yudao.module.campus.controller.admin.esp32.vo.CampusEsp32LogPageReqVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Collections;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CampusEsp32LogServiceImplTest {

    @Test
    void shouldExposeCommitToFirstAudioForCurrentAndHistoricalRows() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(1L);
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(Collections.emptyList());
        when(jdbc.queryForMap(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(new HashMap<>());
        CampusEsp32LogServiceImpl service = new CampusEsp32LogServiceImpl(jdbc);
        CampusEsp32LogPageReqVO query = new CampusEsp32LogPageReqVO();

        service.getPage(query);
        service.getSummary(query);

        ArgumentCaptor<String> pageSql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(pageSql.capture(), any(MapSqlParameterSource.class));
        assertTrue(pageSql.getValue().contains("tts_first_audio_ms - capture_ms"));
        assertTrue(pageSql.getValue().contains("AS commitToFirstAudioMs"));

        ArgumentCaptor<String> summarySql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForMap(summarySql.capture(), any(MapSqlParameterSource.class));
        assertTrue(summarySql.getValue().contains("AS averageCommitToFirstAudioMs"));
    }
}
