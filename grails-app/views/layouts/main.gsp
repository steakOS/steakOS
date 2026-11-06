<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1"/>
    <title><g:layoutTitle default="steakOS"/></title>
    <asset:link rel="icon" href="favicon.ico" type="image/x-ico"/>
    <asset:link rel="icon" href="favicon-32.png" type="image/png"/>
    <asset:link rel="apple-touch-icon" href="apple-touch-icon.png"/>
    <asset:stylesheet src="application.css"/>
    <g:layoutHead/>
</head>

<body>

<style type="text/css">
    .steakos-brand {
        display: flex;
        align-items: center;
        gap: 0.9rem;
        text-decoration: none;
        color: inherit;
        min-width: 0;
    }
    .steakos-brand:hover,
    .steakos-brand:focus {
        color: inherit;
        text-decoration: none;
    }
    .steakos-brand-logo {
        /* logo_small_white.png — white bg matches light login/navbar chrome */
        height: 56px;
        width: auto;
        max-width: min(160px, 40vw);
        flex-shrink: 0;
        object-fit: contain;
        border-radius: 12px;
    }
    .steakos-brand-text {
        display: flex;
        flex-direction: column;
        gap: 0.15rem;
        min-width: 0;
        text-align: left;
    }
    .steakos-brand-title {
        margin: 0;
        font-size: 1.85rem;
        font-weight: 800;
        letter-spacing: -0.03em;
        line-height: 1.1;
        color: #1c1917;
    }
    .steakos-brand-tagline {
        margin: 0;
        font-size: 0.72rem;
        line-height: 1.35;
        color: #78716c;
        font-weight: 500;
        max-width: 28rem;
    }
    @media (max-width: 576px) {
        .steakos-brand-logo {
            height: 44px;
            max-width: 32vw;
        }
        .steakos-brand-title {
            font-size: 1.45rem;
        }
        .steakos-brand-tagline {
            font-size: 0.62rem;
            max-width: 11rem;
        }
    }
</style>
<nav class="navbar navbar-expand-lg bg-body border-bottom shadow-sm">
    <div class="container-lg">
        <a class="navbar-brand steakos-brand m-0" href="${request.contextPath}/">
            <asset:image class="steakos-brand-logo" src="logo_small_white.png" alt="steakOS"/>
            <span class="steakos-brand-text">
                <span class="steakos-brand-title">steakOS</span>
                <span class="steakos-brand-tagline">
                    <g:message code="steakos.brand.tagline"
                               default="A Sovereign Transactional Enterprise Automation Kernel &amp; Orchestration System."/>
                </span>
            </span>
        </a>
        <div class="ms-auto d-flex align-items-center gap-3">
            <sec:ifLoggedIn>
                <span class="text-body-secondary small">
                    <sec:username/>
                </span>
                <form action="${request.contextPath}/logout/index" method="post" class="m-0">
                    <g:if test="${_csrf}">
                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                    </g:if>
                    <button type="submit" class="btn btn-sm btn-outline-secondary">Logout</button>
                </form>
            </sec:ifLoggedIn>
            <sec:ifNotLoggedIn>
                <%-- No Login CTA on the login page itself (avoids a second Login button). --%>
                <g:if test="${!(request.forwardURI ?: request.requestURI ?: '').contains('/login')}">
                    <a class="btn btn-sm btn-primary" href="${request.contextPath}/login/auth">Login</a>
                </g:if>
            </sec:ifNotLoggedIn>
        </div>
    </div>
</nav>

<%
    def _uri = (request.forwardURI ?: request.requestURI ?: '').toString()
    def _isLogin = _uri.contains('/login')
%>
<div class="${_isLogin ? 'steakos-auth-page' : 'bg-body-tertiary'}">
    <style type="text/css">
        .steakos-auth-page {
            min-height: calc(100vh - 11rem);
            background:
                radial-gradient(ellipse 80% 55% at 50% 18%, rgba(232, 93, 4, 0.12), transparent 70%),
                linear-gradient(165deg, #fff 0%, #faf8f6 45%, #f0ebe6 100%);
        }
        .steakos-auth-page > .container-lg {
            padding-top: 1.5rem;
            padding-bottom: 2rem;
        }
    </style>
    <div class="container-lg py-4">
        <g:flashMessages />
        <g:layoutBody/>
    </div>
</div>

<footer class="border-top py-3 bg-body" role="contentinfo">
    <div class="container-lg">
        <%
            def _footerVersion = null
            try {
                def _pvs = grailsApplication.mainContext.getBean('productVersionService')
                _footerVersion = _pvs.current()?.semVerLabel
            } catch (ignored) { /* fall through */ }
            if (!_footerVersion) {
                try {
                    _footerVersion = g.meta(name: 'info.app.version')?.toString()?.trim()
                    if (_footerVersion && !(_footerVersion.startsWith('V') || _footerVersion.startsWith('v'))) {
                        _footerVersion = "V${_footerVersion}"
                    }
                } catch (ignored2) { }
            }
            if (!_footerVersion) {
                _footerVersion = '—'
            }
        %>
        <div class="d-flex align-items-center justify-content-between gap-3">
            <span class="small text-body-secondary" id="steakos-footer-version">${_footerVersion}</span>
            <a class="small text-body-secondary"
               href="https://www.steakos.org"
               target="_blank"
               rel="noopener noreferrer">www.steakOS.org</a>
        </div>
    </div>
</footer>
<div id="spinner" class="position-absolute top-0 end-0 p-1" style="display:none;">
    <div class="spinner-border spinner-border-sm" role="status">
        <span class="visually-hidden">Loading...</span>
    </div>
</div>
<asset:javascript src="application.js"/>
</body>
</html>
