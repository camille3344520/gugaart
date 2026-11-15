/* a few icons sprite.js does not have (same 24-grid, 1.75 stroke) */
document.querySelector('svg defs').insertAdjacentHTML('beforeend', [
  ['sun', '<circle cx="12" cy="12" r="4"/><path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6l1.4 1.4M17 17l1.4 1.4M5.6 18.4L7 17M17 7l1.4-1.4"/>'],
  ['server', '<rect x="4" y="4.500" width="16" height="6" rx="1.500"/><rect x="4" y="13.500" width="16" height="6" rx="1.500"/><path d="M7.500 7.500h.01M7.500 16.500h.01"/>'],
  ['eye', '<path d="M2.500 12s3.500-6.500 9.500-6.500S21.500 12 21.500 12s-3.500 6.500-9.500 6.500S2.500 12 2.500 12z"/><circle cx="12" cy="12" r="2.800"/>'],
  ['warn', '<path d="M12 4l9 15.500H3z"/><path d="M12 10v4.500M12 17.200v.01"/>'],
  ['skip', '<path d="M5 6l7 6-7 6zM13 6l7 6-7 6z" fill="currentColor" stroke="none"/>'],
  ['trash', '<path d="M5 7h14M10 7V4.500h4V7M7 7l1 12.500h8L17 7"/>'],
  ['share', '<path d="M12 15V4M8 7.500L12 4l4 3.500M5 12v7.500h14V12"/>'],
].map((s) => `<symbol id="i-${s[0]}" viewBox="0 0 24 24">${s[1]}</symbol>`).join(''));
