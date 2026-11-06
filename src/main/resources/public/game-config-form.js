// Delegate controls outside the settings form to its native XIS action button.
document.addEventListener('click', event => {
    if (event.target.closest('#download-export')) document.getElementById('export-profile').click();
});
document.addEventListener('invalid', event => {
    const section = event.target.closest('details');
    if (section) section.open = true;
}, true);
