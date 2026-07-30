-- Việt hóa liên kết bạn bè mặc định còn sót. Chỉ đổi đúng seed gốc, không ghi đè tùy chỉnh.
SET NAMES utf8mb4;

UPDATE `friend_link`
SET `link_name` = 'Novel Plus nguồn mở'
WHERE `id` = 5
  AND `link_url` = 'https://novel.xxyopen.com'
  AND `link_name` = '小说精品屋';
