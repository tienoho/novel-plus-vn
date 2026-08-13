/**
 * Khoi-Thu Theme Toggle Script
 * Handles System Preference Detection & LocalStorage Persistence
 */
(function () {
    const THEME_KEY = 'np_theme';
    const root = document.documentElement;

    function getPreferredTheme() {
        const savedTheme = localStorage.getItem(THEME_KEY);
        if (savedTheme === 'dark' || savedTheme === 'light') {
            return savedTheme;
        }
        return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    }

    function applyTheme(theme) {
        root.setAttribute('data-theme', theme);
        const toggleBtn = document.getElementById('themeToggleBtn');
        if (toggleBtn) {
            const isDark = theme === 'dark';
            toggleBtn.setAttribute('aria-label', isDark ? 'Chuyển sang chế độ sáng' : 'Chuyển sang chế độ tối');
            toggleBtn.setAttribute('title', isDark ? 'Chuyển sang chế độ sáng' : 'Chuyển sang chế độ tối');
            const icon = toggleBtn.querySelector('.theme-toggle-icon');
            if (icon) {
                icon.innerHTML = isDark ? '☀️' : '🌙';
            }
        }
    }

    // Apply immediately to prevent flicker
    const initialTheme = getPreferredTheme();
    applyTheme(initialTheme);

    window.toggleTheme = function () {
        const currentTheme = root.getAttribute('data-theme') || 'light';
        const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
        localStorage.setItem(THEME_KEY, newTheme);
        applyTheme(newTheme);
    };

    // System theme change listener
    if (window.matchMedia) {
        window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', function (e) {
            if (!localStorage.getItem(THEME_KEY)) {
                applyTheme(e.matches ? 'dark' : 'light');
            }
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        applyTheme(getPreferredTheme());
    });
})();
