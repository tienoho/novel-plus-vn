package com.java2nb.novel.controller;

import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.core.enums.ResponseStatus;
import com.java2nb.novel.core.exception.BusinessException;
import com.java2nb.novel.dto.reader.ReaderAnnotationCreateRequest;
import com.java2nb.novel.dto.reader.ReaderAnnotationUpdateRequest;
import com.java2nb.novel.dto.reader.ReaderProgressUpdateRequest;
import com.java2nb.novel.service.reader.ReaderAnnotationRow;
import com.java2nb.novel.service.reader.ReaderProgressRow;
import com.java2nb.novel.service.reader.ReaderStateService;
import com.java2nb.novel.service.reader.ReaderStateView;
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

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("user/reader-state")
public class ReaderStateController extends BaseController {
    private final ReaderStateService readerStateService;

    @GetMapping
    public RestResult<ReaderStateView> getState(@RequestParam("bookId") @Min(1) long bookId,
                                                @RequestParam("bookIndexId") @Min(1) long bookIndexId,
                                                HttpServletRequest request) {
        return RestResult.ok(readerStateService.getState(requireUser(request).getId(), bookId, bookIndexId));
    }

    @PutMapping("progress")
    public RestResult<ReaderProgressRow> saveProgress(@Valid @RequestBody ReaderProgressUpdateRequest input,
                                                      HttpServletRequest request) {
        return RestResult.ok(readerStateService.saveProgress(requireUser(request).getId(), input));
    }

    @PostMapping("annotations")
    public RestResult<ReaderAnnotationRow> createAnnotation(
        @Valid @RequestBody ReaderAnnotationCreateRequest input, HttpServletRequest request) {
        return RestResult.ok(readerStateService.createAnnotation(requireUser(request).getId(), input));
    }

    @PutMapping("annotations/{annotationId}")
    public RestResult<ReaderAnnotationRow> updateAnnotation(
        @PathVariable("annotationId") @Min(1) long annotationId,
        @Valid @RequestBody ReaderAnnotationUpdateRequest input,
        HttpServletRequest request) {
        return RestResult.ok(readerStateService.updateAnnotation(requireUser(request).getId(), annotationId, input));
    }

    @DeleteMapping("annotations/{annotationId}")
    public RestResult<Void> deleteAnnotation(
        @PathVariable("annotationId") @Min(1) long annotationId,
        @RequestParam("expectedVersion") @Min(0) long expectedVersion,
        HttpServletRequest request) {
        readerStateService.deleteAnnotation(requireUser(request).getId(), annotationId, expectedVersion);
        return RestResult.ok();
    }

    private UserDetails requireUser(HttpServletRequest request) {
        UserDetails user = getUserDetails(request);
        if (user == null) {
            throw new BusinessException(ResponseStatus.NO_LOGIN);
        }
        return user;
    }
}
