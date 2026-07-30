package com.java2nb.novel.controller;

import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.core.enums.ResponseStatus;
import com.java2nb.novel.core.exception.BusinessException;
import com.java2nb.novel.dto.gamification.AuthorMonthlyRewardResponse;
import com.java2nb.novel.entity.Author;
import com.java2nb.novel.service.AuthorService;
import com.java2nb.novel.service.gamification.AuthorRewardService;
import io.github.xxyopen.model.resp.RestResult;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/author/monthly-rewards")
@RequiredArgsConstructor
public class AuthorGamificationController extends BaseController {

    private final AuthorRewardService authorRewardService;
    private final AuthorService authorService;
    @GetMapping
    public RestResult<List<AuthorMonthlyRewardResponse>> listRewards(HttpServletRequest request) {
        UserDetails user = getUserDetails(request);
        if (user == null) {
            throw new BusinessException(ResponseStatus.NO_LOGIN);
        }
        Author author = authorService.queryAuthor(user.getId());
        if (author == null) {
            throw new BusinessException(ResponseStatus.AUTHOR_STATUS_FORBIDDEN);
        }
        return RestResult.ok(authorRewardService.listAuthorRewards(author.getId(), 100).stream()
            .map(AuthorMonthlyRewardResponse::from).toList());
    }
}
