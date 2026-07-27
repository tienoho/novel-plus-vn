-- Tìm kiếm tiếng Việt có dấu/không dấu và chịu lỗi chính tả nhẹ.
-- Cột sinh giữ nguyên dữ liệu nguồn, tự đồng bộ với mọi luồng ghi và tránh thay đổi
-- collation của unique key (book_name, author_name) đang tồn tại.

SET @schema_name = DATABASE();

SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'book'
          AND column_name = 'book_name_search'
    ),
    'SELECT 1',
    'ALTER TABLE `book` ADD COLUMN `book_name_search` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci GENERATED ALWAYS AS (`book_name`) STORED AFTER `book_name`'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'book'
          AND column_name = 'author_name_search'
    ),
    'SELECT 1',
    'ALTER TABLE `book` ADD COLUMN `author_name_search` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci GENERATED ALWAYS AS (`author_name`) STORED AFTER `author_name`'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = @schema_name
          AND table_name = 'book'
          AND index_name = 'ft_book_vi_search'
    ),
    'SELECT 1',
    'ALTER TABLE `book` ADD FULLTEXT INDEX `ft_book_vi_search` (`book_name_search`, `author_name_search`) WITH PARSER ngram'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

