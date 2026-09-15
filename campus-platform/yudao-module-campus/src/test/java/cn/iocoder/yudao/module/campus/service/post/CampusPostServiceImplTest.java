package cn.iocoder.yudao.module.campus.service.post;

import cn.iocoder.yudao.module.campus.controller.app.post.vo.CampusHotSearchRespVO;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CampusPostServiceImplTest {

    @Test
    void shouldBuildHotSearchFromRealPostTagsAndEngagement() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 15, 10, 0);
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(post("[\"#校园卡\",\"校园卡\",\"校园新鲜事\"]", 200, 20, 8, 12,
                now.minusHours(1)));
        rows.add(post("[\"校园卡\",\"周末活动\"]", 10, 1, 0, 1,
                now.minusHours(12)));
        rows.add(post("[\"旧标签\"]", 5000, 500, 100, 80,
                now.minusDays(30)));

        List<CampusHotSearchRespVO> result = CampusPostServiceImpl.buildHotSearch(rows, 6, now);

        assertEquals(3, result.size());
        assertEquals("校园卡", result.get(0).getKeyword());
        assertEquals(2, result.get(0).getPostCount());
        assertEquals("周末活动", result.get(1).getKeyword());
        assertEquals("旧标签", result.get(2).getKeyword());
    }

    private static Map<String, Object> post(String tagsJson, int views, int likes, int collects, int comments,
                                             LocalDateTime createTime) {
        Map<String, Object> row = new HashMap<>();
        row.put("tags_json", tagsJson);
        row.put("view_count", views);
        row.put("like_count", likes);
        row.put("collect_count", collects);
        row.put("comment_count", comments);
        row.put("create_time", Timestamp.valueOf(createTime));
        return row;
    }
}
