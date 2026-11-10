// XIS page/frontlet insertion does not execute template script tags.
// Load the browser-only Gamepad setup lazily through the supported extension registry.
(function () {
    if (typeof window === 'undefined' || typeof MutationObserver === 'undefined') return;
    var loading = false;
    function loadControllerSetup() {
        if (loading || !document.getElementById('controller-setup')) return;
        loading = true;
        import('/sea-battle/controller-config.js').catch(function () {
            var message = document.getElementById('controller-save-status');
            if (message) message.textContent = 'Die Controller-Einrichtung konnte nicht geladen werden. Bitte die Seite neu laden. Zurück zur Fahrzeugauswahl ist weiterhin möglich.';
        });
    }
    new MutationObserver(loadControllerSetup).observe(document.documentElement, { childList: true, subtree: true });
    loadControllerSetup();
}());
