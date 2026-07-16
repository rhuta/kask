package com.rhuta.kask.ui.markdown

/**
 * Generates the base HTML shell for the WebView.
 * Includes local assets via https://appassets.androidplatform.net/
 * Exactly synchronized with AINI for professional real-time rendering.
 */
fun getHtmlTemplate(isDark: Boolean, initialHtml: String = ""): String {
    val themeClass = if (isDark) "dark-mode" else "light-mode"
    val highlightTheme = if (isDark) "github-dark.min.css" else "github.min.css"
    
    return """
    <!DOCTYPE html>
    <html>
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        
        <link rel="stylesheet" href="https://appassets.androidplatform.net/assets/katex/katex.min.css">
        <script src="https://appassets.androidplatform.net/assets/katex/katex.min.js"></script>
        <script src="https://appassets.androidplatform.net/assets/katex/contrib/auto-render.min.js"></script>
        
        <link rel="stylesheet" href="https://appassets.androidplatform.net/assets/highlight/$highlightTheme">
        <script src="https://appassets.androidplatform.net/assets/highlight/highlight.min.js"></script>
        
        <link rel="stylesheet" href="https://appassets.androidplatform.net/assets/css/markdown-style.css">
        
        <style>
            body { 
                background-color: transparent !important; 
                margin: 0; padding: 0; 
                font-family: -apple-system, system-ui, sans-serif;
                font-size: 15px; line-height: 1.5;
            }
            #content { padding: 12px; overflow-wrap: break-word; }
            .dark-mode { color: #e6edf3; }
            .light-mode { color: #1f2328; }
            
            /* Hide vertical scrollbar for clean bubble look */
            ::-webkit-scrollbar { display: none; }
        </style>
    </head>
    <body class="$themeClass">
        <div id="content">$initialHtml</div>
        <script src="https://appassets.androidplatform.net/assets/js/renderer.js"></script>
    </body>
    </html>
    """.trimIndent()
}
