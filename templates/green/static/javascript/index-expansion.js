/**
 * Novel-Plus Homepage Expansion Script
 * Handles Continue Reading Widget & Genre Chips Navigation
 */
(function () {
    function initContinueReadingWidget() {
        const widgetContainer = document.getElementById('continueReadingWidget');
        if (!widgetContainer) return;

        let historyData = null;
        try {
            const rawHistory = localStorage.getItem('recent_read_history');
            if (rawHistory) {
                historyData = JSON.parse(rawHistory);
            }
        } catch (e) {
            console.warn('Failed to parse reading history', e);
        }

        if (historyData && historyData.bookName && historyData.bookId && historyData.contentId) {
            widgetContainer.innerHTML = `
                <div class="continue-reading-bar glass-card cf">
                    <div class="cr-info fl">
                        <span class="cr-badge">📖 Đang đọc dở</span>
                        <strong class="cr-title">${escapeHtml(historyData.bookName)}</strong>
                        <span class="cr-chapter">${escapeHtml(historyData.indexName || 'Chương vừa đọc')}</span>
                    </div>
                    <div class="cr-action fr">
                        <a href="/book/${historyData.bookId}/${historyData.contentId}.html" class="cr-btn btn_ora">Đọc Tiếp &rarr;</a>
                    </div>
                </div>
            `;
            widgetContainer.style.display = 'block';
        }
    }

    function escapeHtml(str) {
        return String(str || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    document.addEventListener('DOMContentLoaded', function () {
        initContinueReadingWidget();
    });
})();
