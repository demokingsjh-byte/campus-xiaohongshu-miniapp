package cn.iocoder.yudao.module.campus.service.meta;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Set;

@Getter
@AllArgsConstructor
public class CampusResourceMeta {

    private final String resource;
    private final String tableName;
    private final Set<String> creatableColumns;
    private final Set<String> updatableColumns;
    private final Set<String> exactQueryColumns;
    private final Set<String> likeQueryColumns;
}
