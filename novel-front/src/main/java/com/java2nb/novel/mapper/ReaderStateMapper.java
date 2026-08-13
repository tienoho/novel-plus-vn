package com.java2nb.novel.mapper;

import com.java2nb.novel.service.reader.ReaderAnnotationRow;
import com.java2nb.novel.service.reader.ReaderProgressRow;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

public interface ReaderStateMapper {
    ReaderProgressRow selectProgress(@Param("userId") long userId, @Param("bookId") long bookId);

    int upsertProgress(@Param("userId") long userId, @Param("bookId") long bookId,
                       @Param("bookIndexId") long bookIndexId,
                       @Param("paragraphIndex") int paragraphIndex,
                       @Param("characterOffset") int characterOffset,
                       @Param("progressPercent") BigDecimal progressPercent);

    List<ReaderAnnotationRow> listAnnotations(@Param("userId") long userId,
                                              @Param("bookId") long bookId,
                                              @Param("bookIndexId") long bookIndexId);

    ReaderAnnotationRow selectAnnotationOwned(@Param("userId") long userId,
                                               @Param("annotationId") long annotationId);

    int insertAnnotation(ReaderAnnotationRow annotation);

    int updateAnnotationOwned(@Param("userId") long userId,
                              @Param("annotationId") long annotationId,
                              @Param("noteText") String noteText,
                              @Param("expectedVersion") long expectedVersion);

    int deleteAnnotationOwned(@Param("userId") long userId,
                              @Param("annotationId") long annotationId,
                              @Param("expectedVersion") long expectedVersion);
}
