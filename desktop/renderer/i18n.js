// Piccolo motore di traduzione: legge window.EOL_STRINGS (da strings.js)
window.EOL_I18N = (() => {
  let lang = null;

  function detect(){
    try {
      const saved = localStorage.getItem('eol-lang');
      if (saved && window.EOL_STRINGS[saved]) return saved;
    } catch (e) {}
    const nav = (navigator.languages || [navigator.language || 'en']).map(l => l.toLowerCase());
    for (const n of nav){
      const base = n.split('-')[0];
      if (window.EOL_STRINGS[base]) return base;
    }
    return 'en';
  }

  function set(code){
    lang = window.EOL_STRINGS[code] ? code : 'en';
    try { localStorage.setItem('eol-lang', lang); } catch (e) {}
    document.documentElement.lang = lang;
    document.documentElement.dir = window.EOL_RTL.includes(lang) ? 'rtl' : 'ltr';
  }

  function t(key, ...args){
    const dict = window.EOL_STRINGS[lang] || window.EOL_STRINGS.en;
    let s = (dict && dict[key]) || (window.EOL_STRINGS.en && window.EOL_STRINGS.en[key]) || key;
    args.forEach(a => { s = s.replace('%s', a); });
    return s;
  }

  set(detect());
  return { t, set, get: () => lang, names: () => window.EOL_LANG_NAMES, codes: () => window.EOL_LANGS };
})();
