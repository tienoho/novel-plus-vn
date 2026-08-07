package com.java2nb.common.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QueryTest {

    @Test
    void removesUntrustedSortExpression() {
        Query query = new Query(params("user_id desc; drop table sys_user", "asc"));

        assertThat(query).doesNotContainKeys("sort", "order");
    }

    @Test
    void keepsKnownColumnAndNormalizesDirection() {
        Query ascending = new Query(params("gmt_create", "ASC"));
        Query invalidDirection = new Query(params("order_num", "desc; select sleep(5)"));

        assertThat(ascending).containsEntry("sort", "gmt_create").containsEntry("order", "asc");
        assertThat(invalidDirection).containsEntry("sort", "order_num").containsEntry("order", "desc");
    }

    private Map<String, Object> params(String sort, String order) {
        Map<String, Object> params = new HashMap<>();
        params.put("offset", "0");
        params.put("limit", "10");
        params.put("sort", sort);
        params.put("order", order);
        return params;
    }
}
