package cn.iocoder.yudao.module.campus.service;

import cn.iocoder.yudao.framework.common.pojo.PageResult;

import java.util.List;
import java.util.Map;

public interface CampusCrudService {

    Long create(String resource, Map<String, Object> data);

    void update(String resource, Map<String, Object> data);

    void delete(String resource, Long id);

    void deleteList(String resource, List<Long> ids);

    Map<String, Object> get(String resource, Long id);

    PageResult<Map<String, Object>> page(String resource, Map<String, Object> params);
}
