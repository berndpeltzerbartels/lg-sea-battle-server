// Loaded by the XIS root page; inline scripts in dynamically loaded pages do not execute.
(() => {
    let pendingExport = null;
    document.addEventListener('invalid', event => {
        const section = event.target.closest('details');
        if (section) section.open = true;
    }, true);
    document.addEventListener('click', event => {
        if (!event.target.closest('#download-export')) return;
        pendingExport = document.getElementById('download-profile').dataset.exportId;
        document.getElementById('export-profile').click();
    });
    new MutationObserver(() => {
        if (pendingExport === null) return;
        const link = document.getElementById('download-profile');
        if (!link || !link.dataset.exportId || link.dataset.exportId === pendingExport) return;
        pendingExport = null;
        link.click();
    }).observe(document.documentElement, { subtree: true, childList: true, attributes: true, attributeFilter: ['data-export-id'] });
})();
