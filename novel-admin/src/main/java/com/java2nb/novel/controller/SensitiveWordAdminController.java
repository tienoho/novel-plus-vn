package com.java2nb.novel.controller;

import com.java2nb.common.utils.PageBean;
import com.java2nb.common.utils.Query;
import com.java2nb.common.utils.R;
import com.java2nb.novel.core.utils.SensitiveWordFilter;
import com.java2nb.novel.entity.SensitiveWord;
import com.java2nb.novel.mapper.SensitiveWordDynamicSqlSupport;
import com.java2nb.novel.mapper.SensitiveWordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.mybatis.dynamic.sql.render.RenderingStrategies;
import org.mybatis.dynamic.sql.select.render.SelectStatementProvider;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;
import static org.mybatis.dynamic.sql.select.SelectDSL.select;

/**
 * Controller quản lý từ nhạy cảm (Sensitive Word Dictionary Management)
 */
@Controller
@RequestMapping("/novel/sensitiveWord")
@Slf4j
@RequiredArgsConstructor
public class SensitiveWordAdminController {

    private final SensitiveWordMapper sensitiveWordMapper;

    /**
     * Danh sách từ nhạy cảm
     */
    @ResponseBody
    @GetMapping("/list")
    @RequiresPermissions("novel:book:book")
    public R list(@RequestParam Map<String, Object> params) {
        Query query = new Query(params);

        SelectStatementProvider selectStatement = select(SensitiveWordDynamicSqlSupport.sensitiveWord.allColumns())
            .from(SensitiveWordDynamicSqlSupport.sensitiveWord)
            .orderBy(SensitiveWordDynamicSqlSupport.createTime.descending())
            .limit(query.getLimit())
            .offset(query.getOffset())
            .build().render(RenderingStrategies.MYBATIS3);
        List<SensitiveWord> list = sensitiveWordMapper.selectMany(selectStatement);

        SelectStatementProvider countSelect = select(org.mybatis.dynamic.sql.SqlBuilder.count(SensitiveWordDynamicSqlSupport.id))
            .from(SensitiveWordDynamicSqlSupport.sensitiveWord)
            .build().render(RenderingStrategies.MYBATIS3);
        long total = sensitiveWordMapper.count(countSelect);

        PageBean pageBean = new PageBean(list, (int) total);
        return R.ok().put("data", pageBean);
    }

    /**
     * Thêm từ nhạy cảm
     */
    @ResponseBody
    @PostMapping("/save")
    @RequiresPermissions("novel:book:add")
    public R save(SensitiveWord sensitiveWord) {
        if (sensitiveWord.getWord() == null || sensitiveWord.getWord().trim().isEmpty()) {
            return R.error("Từ nhạy cảm không được để trống");
        }
        sensitiveWord.setCreateTime(new Date());
        sensitiveWordMapper.insert(sensitiveWord);

        // Cập nhật vào bộ nhớ DFA Trie
        SensitiveWordFilter.getInstance().addWord(sensitiveWord.getWord().trim());
        return R.ok();
    }

    /**
     * Xóa từ nhạy cảm
     */
    @ResponseBody
    @PostMapping("/remove")
    @RequiresPermissions("novel:book:remove")
    public R remove(Long id) {
        sensitiveWordMapper.delete(c -> c.where(SensitiveWordDynamicSqlSupport.id, isEqualTo(id)));
        return R.ok();
    }
}
