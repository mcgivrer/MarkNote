/**
 * MarkNote Preview JavaScript Module
 * Handles syntax highlighting, diagrams, math equations, and copy buttons.
 */

/**
 * Initialize all preview features.
 * @param {string} mermaidTheme - Mermaid theme ('default' or 'dark')
 */
function initializePreview(mermaidTheme) {
  initializeHighlightJs();
  initializeMermaid(mermaidTheme);
  renderKaTeX();
  initializeCopyButtons();
}

/**
 * Initialize highlight.js syntax highlighting.
 */
function initializeHighlightJs() {
  hljs.highlightAll();
}

/**
 * Initialize Mermaid diagrams.
 * Transforms <pre><code class="language-mermaid"> blocks into <div class="mermaid">.
 * @param {string} theme - Mermaid theme ('default' or 'dark')
 */
function initializeMermaid(theme) {
  document.querySelectorAll('pre code.language-mermaid').forEach(function(block) {
    var pre = block.parentElement;
    var div = document.createElement('div');
    div.className = 'mermaid';
    div.textContent = block.textContent;
    pre.parentNode.replaceChild(div, pre);
  });
  mermaid.initialize({ startOnLoad: true, theme: theme });
}

/**
 * Render KaTeX math expressions.
 * Supports block ($$...$$) and inline ($...$) math.
 */
function renderKaTeX() {
  function decodeHtmlEntities(text) {
    return text.replace(/&amp;/g, '&');
  }

  function renderMath(el) {
    var html = el.innerHTML;
    // Block math: $$...$$
    html = html.replace(/\$\$([\s\S]+?)\$\$/g, function(m, tex) {
      try {
        return katex.renderToString(decodeHtmlEntities(tex).trim(), { displayMode: true, throwOnError: false });
      } catch(e) { return m; }
    });
    // Inline math: $...$ (not preceded by \, not followed by digit)
    html = html.replace(/(?<!\\)\$([^\$\n]+?)\$/g, function(m, tex) {
      try {
        return katex.renderToString(decodeHtmlEntities(tex).trim(), { displayMode: false, throwOnError: false });
      } catch(e) { return m; }
    });
    el.innerHTML = html;
  }
  renderMath(document.body);
}

/**
 * Initialize copy buttons on code blocks.
 */
function initializeCopyButtons() {
  document.querySelectorAll('pre > code').forEach(function(codeEl) {
    var pre = codeEl.parentElement;
    if (pre.querySelector('.copy-btn')) return;
    var btn = document.createElement('button');
    btn.className = 'copy-btn';
    btn.textContent = 'Copy';
    btn.addEventListener('click', function() {
      var text = codeEl.textContent;
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(function() {
          btn.textContent = '\u2713 Copied';
          btn.classList.add('copied');
          setTimeout(function() { btn.textContent = 'Copy'; btn.classList.remove('copied'); }, 1500);
        });
      } else {
        var ta = document.createElement('textarea');
        ta.value = text; ta.style.position = 'fixed'; ta.style.opacity = '0';
        document.body.appendChild(ta); ta.select();
        document.execCommand('copy'); document.body.removeChild(ta);
        btn.textContent = '\u2713 Copied';
        btn.classList.add('copied');
        setTimeout(function() { btn.textContent = 'Copy'; btn.classList.remove('copied'); }, 1500);
      }
    });
    pre.appendChild(btn);
  });
}
