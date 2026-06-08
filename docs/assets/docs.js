(function () {
    const menuButton = document.querySelector('[data-menu-button]');
    const sidebar = document.querySelector('[data-sidebar]');
    const closeButton = document.querySelector('[data-close-sidebar]');
    const overlay = document.querySelector('[data-sidebar-overlay]');

    function closeSidebar() {
        if (!sidebar || !overlay) {
            return;
        }
        sidebar.classList.remove('active');
        overlay.classList.remove('active');
        document.body.style.overflow = '';
    }

    function openSidebar() {
        if (!sidebar || !overlay) {
            return;
        }
        sidebar.classList.add('active');
        overlay.classList.add('active');
        document.body.style.overflow = 'hidden';
    }

    if (menuButton) {
        menuButton.addEventListener('click', openSidebar);
    }
    if (closeButton) {
        closeButton.addEventListener('click', closeSidebar);
    }
    if (overlay) {
        overlay.addEventListener('click', closeSidebar);
    }
    if (sidebar) {
        sidebar.querySelectorAll('a').forEach((link) => {
            link.addEventListener('click', closeSidebar);
        });
    }

    const sections = Array.from(document.querySelectorAll('[id].content-section'));
    const links = Array.from(document.querySelectorAll('.sidebar a[href^="#"]'));
    if (!sections.length || !links.length || !('IntersectionObserver' in window)) {
        return;
    }

    const linkMap = new Map(links.map((link) => [link.getAttribute('href').slice(1), link]));
    const observer = new IntersectionObserver((entries) => {
        const visible = entries
            .filter((entry) => entry.isIntersecting)
            .sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0];
        if (!visible) {
            return;
        }
        links.forEach((link) => link.classList.remove('active'));
        const activeLink = linkMap.get(visible.target.id);
        if (activeLink) {
            activeLink.classList.add('active');
        }
    }, {
        rootMargin: '-20% 0px -65% 0px',
        threshold: [0.05, 0.2, 0.45]
    });

    sections.forEach((section) => observer.observe(section));
}());
