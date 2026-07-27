package com.java2nb.novel.service.search;

import com.java2nb.novel.vo.BookVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VietnameseSearchMatcherTest {

    @Test
    void normalizationPreservesWordsWhileRemovingVietnameseDiacritics() {
        assertThat(VietnameseSearchMatcher.normalize("  Đắc-Nhân Tâm, Nguyễn Nhật Ánh!  "))
            .isEqualTo("dac nhan tam nguyen nhat anh");
    }

    @Test
    void transposedCharactersPreferTheClosestTitleOverLongerAndUnrelatedCandidates() {
        BookVO unrelated = book(4, "Tuổi Trẻ Đáng Giá Bao Nhiêu", "Rosie Nguyễn");
        BookVO longer = book(5, "Đắc Nhân Tâm Thực Hành", "Tác giả thử nghiệm");
        BookVO expected = book(1, "Đắc Nhân Tâm", "Nguyễn Hiến Lê");

        assertThat(VietnameseSearchMatcher.rank("dac nhna tam", List.of(unrelated, longer, expected)))
            .extracting(BookVO::getId)
            .containsExactly(1L, 5L);
    }

    @Test
    void authorTypoMatchesVietnameseNameAndRejectsNoise() {
        BookVO expected = book(3, "Cho Tôi Xin Một Vé Đi Tuổi Thơ", "Nguyễn Nhật Ánh");
        BookVO unrelated = book(4, "Tuổi Trẻ Đáng Giá Bao Nhiêu", "Rosie Nguyễn");

        assertThat(VietnameseSearchMatcher.rank("ngueyn nhat anh", List.of(unrelated, expected)))
            .extracting(BookVO::getId)
            .containsExactly(3L);
        assertThat(VietnameseSearchMatcher.rank("khong lien quan", List.of(unrelated, expected))).isEmpty();
    }

    private BookVO book(long id, String title, String author) {
        BookVO book = new BookVO();
        book.setId(id);
        book.setBookName(title);
        book.setAuthorName(author);
        return book;
    }
}

