// Back-to-Top Floating Button Handler
(function () {
    document.addEventListener('DOMContentLoaded', function () {
        if (!document.getElementById('backToTopBtn')) {
            const btn = document.createElement('button');
            btn.id = 'backToTopBtn';
            btn.type = 'button';
            btn.setAttribute('aria-label', 'Cuộn lên đầu trang');
            btn.setAttribute('title', 'Cuộn lên đầu trang');
            btn.innerHTML = '↑';
            document.body.appendChild(btn);

            window.addEventListener('scroll', function () {
                if (window.scrollY > 300) {
                    btn.classList.add('visible');
                } else {
                    btn.classList.remove('visible');
                }
            }, { passive: true });

            btn.addEventListener('click', function () {
                window.scrollTo({ top: 0, behavior: 'smooth' });
            });
        }
    });
})();
