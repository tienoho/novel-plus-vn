package com.java2nb.novel.controller;

import com.java2nb.common.utils.PageBean;
import com.java2nb.common.utils.Query;
import com.java2nb.common.utils.R;
import com.java2nb.novel.entity.BookOwnershipProof;
import com.java2nb.novel.mapper.BookOwnershipProofDynamicSqlSupport;
import com.java2nb.novel.mapper.BookOwnershipProofMapper;
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

import static org.mybatis.dynamic.sql.select.SelectDSL.select;

/**
 * Controller xác thực bằng chứng sở hữu tác phẩm của tác giả (Ownership Proof Admin)
 */
@Controller
@RequestMapping({"/novel/ownershipProof", "/novel/copyright/proof"})
@Slf4j
@RequiredArgsConstructor
public class OwnershipProofAdminController {

    private final BookOwnershipProofMapper bookOwnershipProofMapper;

    /**
     * Danh sách bằng chứng sở hữu
     */
    @ResponseBody
    @GetMapping("/list")
    @RequiresPermissions("novel:book:book")
    public R list(@RequestParam Map<String, Object> params) {
        Query query = new Query(params);

        SelectStatementProvider selectStatement = select(BookOwnershipProofDynamicSqlSupport.bookOwnershipProof.allColumns())
            .from(BookOwnershipProofDynamicSqlSupport.bookOwnershipProof)
            .orderBy(BookOwnershipProofDynamicSqlSupport.createTime.descending())
            .limit(query.getLimit())
            .offset(query.getOffset())
            .build().render(RenderingStrategies.MYBATIS3);
        List<BookOwnershipProof> list = bookOwnershipProofMapper.selectMany(selectStatement);

        SelectStatementProvider countSelect = select(org.mybatis.dynamic.sql.SqlBuilder.count(BookOwnershipProofDynamicSqlSupport.id))
            .from(BookOwnershipProofDynamicSqlSupport.bookOwnershipProof)
            .build().render(RenderingStrategies.MYBATIS3);
        long total = bookOwnershipProofMapper.count(countSelect);

        PageBean pageBean = new PageBean(list, (int) total);
        return R.ok().put("data", pageBean);
    }

    /**
     * Xác thực bằng chứng sở hữu tác phẩm (1: Hợp lệ, 2: Không hợp lệ)
     */
    @ResponseBody
    @PostMapping("/verify")
    @RequiresPermissions("novel:book:edit")
    public R verify(@RequestParam("id") Long id, @RequestParam("verificationStatus") Byte verificationStatus,
                   @RequestParam(value = "notes", required = false) String notes) {
        BookOwnershipProof proof = bookOwnershipProofMapper.selectByPrimaryKey(id).orElse(null);
        if (proof == null) {
            return R.error("Không tìm thấy dữ liệu bằng chứng sở hữu");
        }

        proof.setVerificationStatus(verificationStatus);
        proof.setNote(notes);
        proof.setUpdateTime(new Date());
        bookOwnershipProofMapper.updateByPrimaryKeySelective(proof);
        return R.ok();
    }
}
