package com.java2nb.novel.controller;

import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.core.enums.ResponseStatus;
import com.java2nb.novel.core.exception.BusinessException;
import com.java2nb.novel.dto.author.StoryItemCreateRequest;
import com.java2nb.novel.dto.author.StoryItemUpdateRequest;
import com.java2nb.novel.entity.Author;
import com.java2nb.novel.service.AuthorService;
import com.java2nb.novel.service.story.AuthorStoryItemRow;
import com.java2nb.novel.service.story.AuthorStoryService;
import io.github.xxyopen.model.resp.RestResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("author/story-items")
public class AuthorStoryController extends BaseController {
    private final AuthorService authorService;
    private final AuthorStoryService storyService;

    @GetMapping
    public RestResult<List<AuthorStoryItemRow>> list(@RequestParam("bookId") long bookId,
                                                     @RequestParam(value = "type", required = false) String type,
                                                     HttpServletRequest request) {
        return RestResult.ok(storyService.list(requireAuthor(request).getId(), bookId, type));
    }

    @PostMapping
    public RestResult<AuthorStoryItemRow> create(@Valid @RequestBody StoryItemCreateRequest input,
                                                 HttpServletRequest request) {
        return RestResult.ok(storyService.create(requireAuthor(request).getId(), input));
    }

    @PutMapping("{itemId}")
    public RestResult<AuthorStoryItemRow> update(@PathVariable("itemId") long itemId,
                                                 @Valid @RequestBody StoryItemUpdateRequest input,
                                                 HttpServletRequest request) {
        return RestResult.ok(storyService.update(requireAuthor(request).getId(), itemId, input));
    }

    @DeleteMapping("{itemId}")
    public RestResult<Void> delete(@PathVariable("itemId") long itemId,
                                   @RequestParam("expectedVersion") long expectedVersion,
                                   HttpServletRequest request) {
        storyService.delete(requireAuthor(request).getId(), itemId, expectedVersion);
        return RestResult.ok();
    }

    private Author requireAuthor(HttpServletRequest request) {
        UserDetails user = getUserDetails(request);
        if (user == null) {
            throw new BusinessException(ResponseStatus.NO_LOGIN);
        }
        if (!authorService.isAuthor(user.getId())) {
            throw new BusinessException(ResponseStatus.BOOK_NOT_AVAILABLE);
        }
        Author author = authorService.queryAuthor(user.getId());
        if (author.getStatus() != null && author.getStatus() == 1) {
            throw new BusinessException(ResponseStatus.AUTHOR_STATUS_FORBIDDEN);
        }
        return author;
    }
}
