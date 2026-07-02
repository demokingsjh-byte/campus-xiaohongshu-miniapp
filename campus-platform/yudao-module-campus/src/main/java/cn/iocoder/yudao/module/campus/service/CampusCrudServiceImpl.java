package cn.iocoder.yudao.module.campus.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.campus.service.meta.CampusResourceMeta;
import cn.iocoder.yudao.module.campus.service.meta.CampusResourceRegistry;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CampusCrudServiceImpl implements CampusCrudService {

    private static final String ID = "id";
    private static final String PAGE_NO = "pageNo";
    private static final String PAGE_SIZE = "pageSize";

    @Resource
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(String resource, Map<String, Object> data) {
        CampusResourceMeta meta = CampusResourceRegistry.get(resource);
        Map<String, Object> values = filterWritable(meta, data, false);
        values.putIfAbsent("tenant_id", 0L);
        values.putIfAbsent("creator", "");
        values.putIfAbsent("updater", "");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("创建数据不能为空");
        }
        String columns = String.join(", ", values.keySet());
        String params = values.keySet().stream().map(column -> ":" + column).collect(Collectors.joining(", "));
        String sql = "INSERT INTO " + meta.getTableName() + " (" + columns + ", create_time, update_time, deleted) "
                + "VALUES (" + params + ", NOW(), NOW(), b'0')";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedParameterJdbcTemplate.update(sql, new MapSqlParameterSource(values), keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String resource, Map<String, Object> data) {
        CampusResourceMeta meta = CampusResourceRegistry.get(resource);
        Long id = Convert.toLong(data.get(ID));
        if (id == null) {
            throw new IllegalArgumentException("id 不能为空");
        }
        Map<String, Object> values = filterWritable(meta, data, true);
        if (values.isEmpty()) {
            return;
        }
        values.put(ID, id);
        values.put("updater", Convert.toStr(data.getOrDefault("updater", "")));
        String sets = values.keySet().stream()
                .filter(column -> !Objects.equals(column, ID))
                .map(column -> column + " = :" + column)
                .collect(Collectors.joining(", "));
        String sql = "UPDATE " + meta.getTableName() + " SET " + sets
                + ", update_time = NOW() WHERE id = :id AND deleted = b'0'";
        namedParameterJdbcTemplate.update(sql, values);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String resource, Long id) {
        CampusResourceMeta meta = CampusResourceRegistry.get(resource);
        namedParameterJdbcTemplate.update("UPDATE " + meta.getTableName()
                + " SET deleted = b'1', update_time = NOW() WHERE id = :id",
                new MapSqlParameterSource(ID, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteList(String resource, List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        CampusResourceMeta meta = CampusResourceRegistry.get(resource);
        namedParameterJdbcTemplate.update("UPDATE " + meta.getTableName()
                + " SET deleted = b'1', update_time = NOW() WHERE id IN (:ids)",
                new MapSqlParameterSource("ids", ids));
    }

    @Override
    public Map<String, Object> get(String resource, Long id) {
        CampusResourceMeta meta = CampusResourceRegistry.get(resource);
        List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(
                "SELECT * FROM " + meta.getTableName() + " WHERE id = :id AND deleted = b'0' LIMIT 1",
                new MapSqlParameterSource(ID, id));
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public PageResult<Map<String, Object>> page(String resource, Map<String, Object> params) {
        CampusResourceMeta meta = CampusResourceRegistry.get(resource);
        Integer pageNo = Math.max(Convert.toInt(params.get(PAGE_NO), 1), 1);
        Integer pageSize = Math.min(Math.max(Convert.toInt(params.get(PAGE_SIZE), 10), 1), 200);
        MapSqlParameterSource sqlParams = new MapSqlParameterSource()
                .addValue("offset", (pageNo - 1) * pageSize)
                .addValue("pageSize", pageSize);
        String where = buildWhere(meta, params, sqlParams);
        Long total = namedParameterJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + meta.getTableName() + where,
                sqlParams, Long.class);
        List<Map<String, Object>> list = namedParameterJdbcTemplate.queryForList(
                "SELECT * FROM " + meta.getTableName() + where + " ORDER BY id DESC LIMIT :offset, :pageSize",
                sqlParams);
        return new PageResult<>(list, total == null ? 0 : total);
    }

    private Map<String, Object> filterWritable(CampusResourceMeta meta, Map<String, Object> data, boolean update) {
        Map<String, Object> values = new LinkedHashMap<>();
        Set<String> allowedColumns = update ? meta.getUpdatableColumns() : meta.getCreatableColumns();
        for (String column : allowedColumns) {
            if (data.containsKey(column)) {
                values.put(column, data.get(column));
            }
        }
        return values;
    }

    private String buildWhere(CampusResourceMeta meta, Map<String, Object> params, MapSqlParameterSource sqlParams) {
        List<String> conditions = new ArrayList<>();
        conditions.add(" deleted = b'0'");
        for (String column : meta.getExactQueryColumns()) {
            Object value = params.get(column);
            if (value != null && StrUtil.isNotBlank(Convert.toStr(value))) {
                conditions.add(column + " = :" + column);
                sqlParams.addValue(column, value);
            }
        }
        for (String column : meta.getLikeQueryColumns()) {
            String value = Convert.toStr(params.get(column), "");
            if (StrUtil.isNotBlank(value)) {
                conditions.add(column + " LIKE :" + column);
                sqlParams.addValue(column, "%" + value + "%");
            }
        }
        return " WHERE " + String.join(" AND ", conditions);
    }
}
