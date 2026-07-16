const contentDiv = document.getElementById('content');

function updateContent(html) {
    if (!html) return;

    // Inject the new HTML
    contentDiv.innerHTML = html;

    console.log("AINI_DEBUG: HTML Content -> " + html);

    // 1. Pre-process: Handle various ways Markdown parsers wrap math
    // Some parsers use <pre class="math">, others use <div class="math">
    document.querySelectorAll('.math, .language-math, pre, code').forEach((el) => {
        const text = el.innerText.trim();
        // If it looks like display math but is trapped in an element KaTeX ignores
        if (text.startsWith('$$') && text.endsWith('$$')) {
            const container = el.closest('pre') || el;
            const mathDiv = document.createElement('div');
            mathDiv.innerText = text;
            container.replaceWith(mathDiv);
        }
    });

    // 2. Syntax highlighting for actual code and add copy buttons
    if (window.hljs) {
        document.querySelectorAll('pre code').forEach((block) => {
            if (!block.innerText.trim().startsWith('$$')) {
                hljs.highlightElement(block);

                // Add Copy Button
                const pre = block.parentElement;
                if (pre && pre.tagName === 'PRE') {
                    const button = document.createElement('button');
                    button.innerText = 'Copy';
                    button.className = 'copy-button';
                    button.onclick = () => {
                        const text = block.innerText;
                        if (window.Android && window.Android.copyToClipboard) {
                            window.Android.copyToClipboard(text);
                            button.innerText = 'Copied!';
                            setTimeout(() => { button.innerText = 'Copy'; }, 2000);
                        }
                    };
                    pre.appendChild(button);
                }
            }
        });
    }

    // 3. Render LaTeX
    if (typeof renderMathInElement === 'function') {
        try {
            renderMathInElement(contentDiv, {
                delimiters: [
                    {left: "$$", right: "$$", display: true},
                    {left: "$", right: "$", display: false},
                    {left: "\\(", right: "\\)", display: false},
                    {left: "\\[", right: "\\]", display: true}
                ],
                throwOnError: false,
                trust: true,
                strict: false,
                // CRITICAL: Tell KaTeX NOT to ignore any tags while we debug
                ignoredTags: ["script", "noscript", "style", "textarea", "option"]
            });
        } catch (e) {
            console.error("AINI_DEBUG: KaTeX Error -> " + e.message);
        }
    }

    setTimeout(() => {
        if (window.Android) {
            window.Android.onHeightChanged(document.body.scrollHeight);
        }
    }, 100);
}
