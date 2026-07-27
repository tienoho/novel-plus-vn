package com.java2nb.novel.service.story;

import com.java2nb.novel.core.enums.ResponseStatus;
import com.java2nb.novel.core.exception.BusinessException;
import com.java2nb.novel.dto.author.StoryItemCreateRequest;
import com.java2nb.novel.dto.author.StoryItemUpdateRequest;
import com.java2nb.novel.mapper.AuthorStoryMapper;
import com.java2nb.novel.service.collaboration.AuthorBookAccess;
import com.java2nb.novel.service.collaboration.AuthorBookCollaborationService;
import com.java2nb.novel.service.collaboration.BookPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthorStoryServiceImpl implements AuthorStoryService {
    private final AuthorStoryMapper mapper;
    private final AuthorBookCollaborationService collaborationService;

    @Override
    @Transactional(readOnly = true)
    public List<AuthorStoryItemRow> list(long authorId, long bookId, String type) {
        AuthorBookAccess access = collaborationService.requirePermission(
            authorId, bookId, BookPermission.MANAGE_STORY);
        String normalizedType = type == null || type.isBlank() ? null : AuthorStoryItemType.parse(type).name();
        return mapper.list(access.getOwnerAuthorId(), bookId, normalizedType);
    }

    @Override
    @Transactional
    public AuthorStoryItemRow create(long authorId, StoryItemCreateRequest input) {
        AuthorBookAccess access = collaborationService.requirePermission(
            authorId, input.getBookId(), BookPermission.MANAGE_STORY);
        AuthorStoryItemRow item = new AuthorStoryItemRow();
        item.setAuthorId(access.getOwnerAuthorId());
        item.setBookId(input.getBookId());
        apply(item, input.getType(), input.getTitle(), input.getContent(), input.getTimelineLabel(),
            input.getSortOrder());
        item.setVersion(0L);
        mapper.insert(item);
        return requireItem(access.getOwnerAuthorId(), item.getId());
    }

    @Override
    @Transactional
    public AuthorStoryItemRow update(long authorId, long itemId, StoryItemUpdateRequest input) {
        AccessibleItem accessible = requireAccessibleItem(authorId, itemId);
        long ownerAuthorId = accessible.access().getOwnerAuthorId();
        AuthorStoryItemRow normalized = new AuthorStoryItemRow();
        apply(normalized, input.getType(), input.getTitle(), input.getContent(), input.getTimelineLabel(),
            input.getSortOrder());
        int updated = mapper.updateOwned(ownerAuthorId, itemId, normalized.getType(), normalized.getTitle(),
            normalized.getContent(), normalized.getTimelineLabel(), normalized.getSortOrder(),
            input.getExpectedVersion());
        if (updated != 1) {
            if (mapper.selectOwned(ownerAuthorId, itemId) == null) {
                throw new BusinessException(ResponseStatus.AUTHOR_STORY_ITEM_NOT_FOUND);
            }
            throw new BusinessException(ResponseStatus.AUTHOR_STORY_VERSION_CONFLICT);
        }
        return requireItem(ownerAuthorId, itemId);
    }

    @Override
    @Transactional
    public void delete(long authorId, long itemId, long expectedVersion) {
        AccessibleItem accessible = requireAccessibleItem(authorId, itemId);
        long ownerAuthorId = accessible.access().getOwnerAuthorId();
        if (mapper.deleteOwned(ownerAuthorId, itemId, expectedVersion) != 1) {
            if (mapper.selectOwned(ownerAuthorId, itemId) == null) {
                throw new BusinessException(ResponseStatus.AUTHOR_STORY_ITEM_NOT_FOUND);
            }
            throw new BusinessException(ResponseStatus.AUTHOR_STORY_VERSION_CONFLICT);
        }
    }

    private AuthorStoryItemRow requireItem(long ownerAuthorId, long itemId) {
        AuthorStoryItemRow item = mapper.selectOwned(ownerAuthorId, itemId);
        if (item == null) {
            throw new BusinessException(ResponseStatus.AUTHOR_STORY_ITEM_NOT_FOUND);
        }
        return item;
    }

    private AccessibleItem requireAccessibleItem(long actorAuthorId, long itemId) {
        AuthorStoryItemRow item = mapper.selectById(itemId);
        if (item == null) {
            throw new BusinessException(ResponseStatus.AUTHOR_STORY_ITEM_NOT_FOUND);
        }
        AuthorBookAccess access = collaborationService.requirePermission(
            actorAuthorId, item.getBookId(), BookPermission.MANAGE_STORY);
        if (!access.getOwnerAuthorId().equals(item.getAuthorId())) {
            throw new BusinessException(ResponseStatus.AUTHOR_STORY_ITEM_NOT_FOUND);
        }
        return new AccessibleItem(item, access);
    }

    private void apply(AuthorStoryItemRow item, String type, String title, String content,
                       String timelineLabel, Integer sortOrder) {
        item.setType(AuthorStoryItemType.parse(type).name());
        item.setTitle(title == null ? "" : title.trim());
        item.setContent(content == null ? "" : content);
        item.setTimelineLabel(timelineLabel == null || timelineLabel.isBlank() ? null : timelineLabel.trim());
        item.setSortOrder(sortOrder == null ? 0 : sortOrder);
    }

    private record AccessibleItem(AuthorStoryItemRow item, AuthorBookAccess access) {
    }
}
