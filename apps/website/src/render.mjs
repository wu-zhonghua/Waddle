import { copy, editions } from './content.mjs';

export const escapeHTML = value => String(value).replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char]);
const paths = {
  terminal: '<rect x="3" y="4" width="18" height="16" rx="2"/><path d="m7 9 3 3-3 3m6 0h4"/>',
  folder: '<path d="M3 7V5h6l2 3h10v12H3Z"/>',
  keys: '<rect x="2" y="5" width="20" height="14" rx="2"/><path d="M6 9h1m4 0h1m4 0h1M6 13h1m4 0h1m4 0h1M8 16h8"/>',
  phone: '<rect x="6" y="2" width="12" height="20" rx="2"/><path d="M10 18h4"/>',
  desktop: '<rect x="2" y="3" width="20" height="14" rx="2"/><path d="M12 17v4m-5 0h10"/>',
};
const icon = name => `<svg viewBox="0 0 24 24" aria-hidden="true">${paths[name]}</svg>`;

export function render({ language, distribution, screenshots }) {
  const t = key => {
    if (!copy[key]?.[language]) throw new Error(`Missing ${language} copy: ${key}`);
    return escapeHTML(copy[key][language]);
  };
  const domestic = distribution === 'domestic';
  const assets = editions[distribution];
  const shot = (file, alt, caption, { hero = false, wide = false } = {}) => {
    const { width, height } = screenshots[file];
    return `<figure class="shot ${wide ? '' : 'phone'} feature-media">
      <a href="./assets/${file}" aria-label="${t(alt)}"><img src="./assets/${file}" width="${width}" height="${height}" alt="${t(alt)}" ${hero ? 'fetchpriority="high"' : 'loading="lazy"'} decoding="async"></a>
      <figcaption>${t(caption)}</figcaption>
    </figure>`;
  };
  const terminal = (command, caption) => `<figure class="terminal feature-media">
    <div class="terminal-label">${icon('terminal')}<code>~/demo</code></div>
    <pre><code>${escapeHTML(command)}</code></pre><figcaption>${t(caption)}</figcaption>
  </figure>`;
  return `<!doctype html>
<html lang="${language === 'zh' ? 'zh-CN' : 'en'}">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="description" content="${t('description')}">
  <title>${t('title')}</title>
  <link rel="icon" href="./favicon.svg" type="image/svg+xml">
  <link rel="stylesheet" href="./styles.css">
  <link rel="alternate" hreflang="en" href="./index.html">
  <link rel="alternate" hreflang="zh-CN" href="./zh.html">
</head>
<body>
  <a class="skip-link" href="#main">${t('skip')}</a>
  <header class="site-header">
    <nav class="container header-inner" aria-label="${t('navigation')}">
      <a class="brand" href="${language === 'zh' ? './zh.html' : './index.html'}">${icon('terminal')}Waddle</a>
      <div class="header-actions">
        <div class="languages" aria-label="${t('language')}">
          <a href="./index.html" lang="en" hreflang="en" ${language === 'en' ? 'aria-current="page"' : ''}>English</a>
          <a href="./zh.html" lang="zh-CN" hreflang="zh-CN" ${language === 'zh' ? 'aria-current="page"' : ''}>中文</a>
        </div>
        <a class="nav-link" href="https://github.com/wu-zhonghua/Waddle">GitHub</a>
      </div>
    </nav>
  </header>
  <main id="main" class="container">
    <section class="hero" aria-labelledby="hero-title">
      <div class="hero-copy">
        <h1 id="hero-title">${t('hero')}</h1>
        <p>${t('intro')}</p>
        <a class="primary" href="#downloads">${t('downloadAction')}</a>
      </div>
      ${shot(assets.hero, 'heroAlt', 'heroCaption', { hero: true })}
    </section>
    <section aria-labelledby="features-title">
      <h2 class="section-title" id="features-title">${t('features')}</h2>
      <article class="feature reverse">
        <div class="feature-copy">
          <h3>${t('terminalTitle')}</h3>
          <p>${t('terminalBody')}</p>
          <ul>
            <li>${icon('terminal')}<span>${t('terminalLocal')}</span></li>
            <li>${icon('folder')}<span>${t('terminalFiles')}</span></li>
            <li>${icon('keys')}<span>${t('terminalKeys')}</span></li>
          </ul>
        </div>
        ${terminal('~/demo $ ls\nREADME.md  notes.txt  src\n\n~/demo $ cat notes.txt\nWaddle workspace\n\n~/demo $', 'terminalCaption')}
      </article>
      <article class="feature">
        <div class="feature-copy">
          <h3>${t('aiTitle')}</h3><p>${t('aiBody')}</p>
          <p>${t('aiProviders')}${domestic ? '' : `<br>${t('aiProvidersInternational')}`}</p>
        </div>
        ${shot(assets.ai, domestic ? 'aiAltDomestic' : 'aiAlt', 'aiCaption')}
      </article>
      ${domestic ? '' : `<article class="feature reverse">
        <div class="feature-copy">
          <h3>${t('bundledTitle')}</h3><p>${t('bundledBody').replace(/\bcodex\b/g, '<code>codex</code>')}</p><p>${t('bundledFuture')}</p>
        </div>
        ${terminal('~/demo $ codex', 'bundledCaption')}
      </article>`}
      <article class="feature reverse">
        <div class="feature-copy">
          <h3>${t('accountsTitle')}</h3><p>${t(domestic ? 'accountsBodyDomestic' : 'accountsBody')}</p>
        </div>
        ${shot(assets.accounts, domestic ? 'accountsAltDomestic' : 'accountsAlt', domestic ? 'accountsCaptionDomestic' : 'accountsCaption')}
      </article>
      <article class="feature foldable">
        <div class="feature-copy"><h3>${t('foldTitle')}</h3><p>${t('foldBody')}</p></div>
        ${shot(assets.fold, 'foldAlt', 'foldCaption', { wide: true })}
      </article>
    </section>
    <section id="downloads" class="downloads" aria-labelledby="downloads-title">
      <h2 id="downloads-title">${t('downloads')}</h2><p>${t('downloadNotice')}</p>
      <div class="download-list">
        ${[t('android'), `${t('desktop')} · macOS`, `${t('desktop')} · Windows`, `${t('desktop')} · Linux`].map((platform, index) => `<div class="download-row"><div class="download-platform">${icon(index === 0 ? 'phone' : 'desktop')}<span>${platform}</span></div><span>${t('comingSoon')}</span></div>`).join('\n')}
      </div>
    </section>
  </main>
  <footer class="site-footer">
    <div class="container footer-inner">
      <div class="footer-brand"><strong>Waddle</strong><p>${t('byline')}</p></div>
      <div class="licenses"><a href="https://github.com/wu-zhonghua/Waddle/blob/main/apps/android/LICENSE">${t('androidLicense')}</a><a href="https://github.com/wu-zhonghua/Waddle/blob/main/LICENSE">${t('desktopLicense')}</a></div>
      <a class="nav-link" href="https://github.com/wu-zhonghua/Waddle">GitHub</a>
    </div>
  </footer>
</body>
</html>\n`;
}
