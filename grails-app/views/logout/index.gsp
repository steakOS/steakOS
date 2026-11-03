<!doctype html>
<html lang="${request.getAttribute('steakosUiLang') ?: request.locale?.language ?: 'en'}">
<head>
    <meta charset="utf-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1"/>
    <meta name="layout" content=""/>
    <title><g:message code='steakos.logout.thanks'/> steakOS</title>
    <asset:link rel="icon" href="favicon.ico" type="image/x-ico"/>
    <asset:stylesheet src="application.css"/>
    <style>
        :root {
            --steak-orange: #e85d04;
            --steak-orange-soft: #f48c06;
            --steak-ink: #1a1a1a;
            --steak-muted: #5c5c5c;
            --steak-bg: #faf8f6;
        }

        html, body {
            height: 100%;
            margin: 0;
        }

        body.logout-thanks {
            min-height: 100vh;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            background:
                radial-gradient(ellipse 80% 60% at 50% 40%, rgba(232, 93, 4, 0.12), transparent 70%),
                linear-gradient(165deg, #fff 0%, var(--steak-bg) 45%, #f0ebe6 100%);
            font-family: system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
            color: var(--steak-ink);
            text-align: center;
            padding: 2rem 1.25rem;
        }

        .thanks-card {
            max-width: 56rem;
            width: 100%;
            padding: 3rem 1.5rem 2.5rem;
        }

        .thanks-line {
            margin: 0;
            font-weight: 500;
            font-size: clamp(1.75rem, 4.5vw, 3rem);
            letter-spacing: -0.02em;
            color: var(--steak-muted);
            line-height: 1.25;
        }

        .thanks-brand {
            margin: 0.35em 0 0;
            font-weight: 800;
            font-size: clamp(3.5rem, 14vw, 8.5rem);
            line-height: 0.95;
            letter-spacing: -0.04em;
            background: linear-gradient(120deg, var(--steak-orange) 0%, var(--steak-orange-soft) 55%, #ffba08 100%);
            -webkit-background-clip: text;
            background-clip: text;
            color: transparent;
            text-shadow: 0 0 80px rgba(232, 93, 4, 0.25);
            animation: brand-in 0.7s ease-out both;
        }

        .thanks-rule {
            width: min(12rem, 40%);
            height: 4px;
            margin: 2rem auto 1.75rem;
            border: 0;
            border-radius: 2px;
            background: linear-gradient(90deg, transparent, var(--steak-orange), transparent);
            opacity: 0.85;
        }

        .thanks-actions {
            display: flex;
            flex-wrap: wrap;
            gap: 0.75rem;
            justify-content: center;
            margin-top: 0.5rem;
        }

        .thanks-actions .btn {
            min-width: 8.5rem;
            border-radius: 999px;
            padding: 0.55rem 1.4rem;
            font-weight: 600;
        }

        .btn-steak {
            background-color: var(--steak-orange);
            border-color: var(--steak-orange);
            color: #fff;
        }

        .btn-steak:hover,
        .btn-steak:focus {
            background-color: #d00000;
            border-color: #d00000;
            color: #fff;
        }

        @keyframes brand-in {
            from {
                opacity: 0;
                transform: translateY(0.4em) scale(0.98);
            }
            to {
                opacity: 1;
                transform: translateY(0) scale(1);
            }
        }

        @media (prefers-reduced-motion: reduce) {
            .thanks-brand {
                animation: none;
            }
        }
    </style>
</head>
<body class="logout-thanks">
    <main class="thanks-card" role="main">
        <p class="thanks-line"><g:message code='steakos.logout.thanks'/></p>
        <h1 class="thanks-brand">steakOS</h1>
        <hr class="thanks-rule" aria-hidden="true"/>
        <div class="thanks-actions">
            <a class="btn btn-steak" id="sign-in-again" href="${request.contextPath}/login/auth?lang=${request.getAttribute('steakosUiLocale') ?: request.getAttribute('steakosUiLang') ?: request.locale?.language ?: 'en'}"><g:message code='steakos.logout.signInAgain'/></a>
        </div>
    </main>
    <script>
        (function persistLocaleAcrossLogout() {
            var key = 'steakos.ui.locale';
            function readCookie(name) {
                try {
                    var parts = document.cookie ? document.cookie.split(';') : [];
                    for (var i = 0; i < parts.length; i++) {
                        var p = parts[i];
                        var idx = p.indexOf('=');
                        if (idx < 0) continue;
                        if (p.slice(0, idx).trim() !== name) continue;
                        return decodeURIComponent(p.slice(idx + 1).trim());
                    }
                } catch (e) {}
                return null;
            }
            function writeCookies(full) {
                if (!full) return;
                var short = String(full).split(/[_-]/)[0].toLowerCase();
                try { localStorage.setItem(key, full); } catch (e) {}
                try {
                    document.cookie = 'STEAKOS_LANG=' + encodeURIComponent(short) + '; path=/; SameSite=Lax; max-age=31536000';
                    document.cookie = 'STEAKOS_UI_LOCALE=' + encodeURIComponent(full) + '; path=/; SameSite=Lax; max-age=31536000';
                } catch (e) {}
                var link = document.getElementById('sign-in-again');
                if (link) {
                    try {
                        var href = new URL(link.href, window.location.origin);
                        href.searchParams.set('lang', full);
                        link.href = href.pathname + href.search;
                    } catch (e) {}
                }
            }
            try {
                var url = new URL(window.location.href);
                var fromUrl = url.searchParams.get('locale') || url.searchParams.get('lang');
                var stored = null;
                try { stored = localStorage.getItem(key); } catch (e) {}
                var fromCookie = readCookie('STEAKOS_UI_LOCALE') || readCookie('STEAKOS_LANG');
                var full = fromUrl || stored || fromCookie;
                if (full) {
                    // Normalize short codes to a default full locale for the login picker.
                    if (full.indexOf('_') < 0 && full.indexOf('-') < 0) {
                        if (full === 'de') full = 'de_DE';
                        else if (full === 'es') full = 'es_ES';
                        else if (full === 'en') full = 'en_US';
                    }
                    writeCookies(full);
                    var short = String(full).split(/[_-]/)[0].toLowerCase();
                    var pageLang = (document.documentElement.lang || 'en').toLowerCase().split(/[_-]/)[0];
                    var urlLang = (url.searchParams.get('lang') || '').toLowerCase().split(/[_-]/)[0];
                    if (pageLang !== short && urlLang !== short) {
                        url.searchParams.set('lang', full);
                        url.searchParams.set('locale', full);
                        window.location.replace(url.toString());
                        return;
                    }
                }
            } catch (e) {}
            // Drop SPA-only flags; never clear steakos.ui.locale or STEAKOS_* locale cookies.
            try { sessionStorage.clear(); } catch (e) {}
            try { localStorage.removeItem('steakos.setupCelebration'); } catch (e) {}
        })();
    </script>
</body>
</html>
