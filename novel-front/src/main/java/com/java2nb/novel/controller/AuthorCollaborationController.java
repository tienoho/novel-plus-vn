package com.java2nb.novel.controller;

import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.core.enums.ResponseStatus;
import com.java2nb.novel.core.exception.BusinessException;
import com.java2nb.novel.dto.author.BookCollaboratorCreateRequest;
import com.java2nb.novel.dto.author.BookCollaboratorUpdateRequest;
import com.java2nb.novel.entity.Author;
import com.java2nb.novel.service.AuthorService;
import com.java2nb.novel.service.collaboration.AuthorBookAccess;
import com.java2nb.novel.service.collaboration.AuthorBookCollaborationService;
import com.java2nb.novel.service.collaboration.AuthorBookCollaboratorRow;
import io.github.xxyopen.model.resp.RestResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
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

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("author/books/{bookId}")
public class AuthorCollaborationController extends BaseController {
    private final AuthorService authorService;
    private final AuthorBookCollaborationService collaborationService;

    @GetMapping("access")
    public RestResult<AuthorBookAccess> access(@PathVariable("bookId") long bookId,
                                               HttpServletRequest request) {
        Author actor = requireAuthor(request);
        AuthorBookAccess access = collaborationService.getAccess(actor.getId(), bookId);
        if (access == null) {
            throw new BusinessException(ResponseStatus.AUTHOR_BOOK_ACCESS_FORBIDDEN);
        }
        return RestResult.ok(access);
    }

    @GetMapping("collaborators")
    public RestResult<List<AuthorBookCollaboratorRow>> list(@PathVariable("bookId") long bookId,
                                                            HttpServletRequest request) {
        return RestResult.ok(collaborationService.list(requireAuthor(request).getId(), bookId));
    }

    @PostMapping("collaborators")
    public RestResult<AuthorBookCollaboratorRow> add(@PathVariable("bookId") long bookId,
                                                     @Valid @RequestBody BookCollaboratorCreateRequest input,
                                                     HttpServletRequest request) {
        return RestResult.ok(collaborationService.add(requireAuthor(request).getId(), bookId, input));
    }

    @PutMapping("collaborators/{collaboratorId}")
    public RestResult<AuthorBookCollaboratorRow> update(
        @PathVariable("bookId") long bookId,
        @PathVariable("collaboratorId") long collaboratorId,
        @Valid @RequestBody BookCollaboratorUpdateRequest input,
        HttpServletRequest request) {
        return RestResult.ok(collaborationService.update(
            requireAuthor(request).getId(), bookId, collaboratorId, input));
    }

    @DeleteMapping("collaborators/{collaboratorId}")
    public RestResult<Void> remove(
        @PathVariable("bookId") long bookId,
        @PathVariable("collaboratorId") long collaboratorId,
        @RequestParam("expectedVersion") @Min(0) long expectedVersion,
        HttpServletRequest request) {
        collaborationService.remove(requireAuthor(request).getId(), bookId, collaboratorId, expectedVersion);
        return RestResult.ok();
    }

    private Author requireAuthor(HttpServletRequest request) {
        UserDetails user = getUserDetails(request);
        if (user == null) {
            throw new BusinessException(ResponseStatus.NO_LOGIN);
        }
        if (!authorService.isAuthor(user.getId())) {
            throw new BusinessException(ResponseStatus.AUTHOR_BOOK_ACCESS_FORBIDDEN);
        }
        Author author = authorService.queryAuthor(user.getId());
        if (author.getStatus() != null && author.getStatus() == 1) {
            throw new BusinessException(ResponseStatus.AUTHOR_STATUS_FORBIDDEN);
        }
        return author;
    }
}
