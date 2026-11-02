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
        /* logo_small_white.png from artefacts/images/new */
        height: 56px;
        width: auto;
        max-width: min(160px, 40vw);
        flex-shrink: 0;
        object-fit: contain;
        border-radius: 12px;
        background: #fff;
        box-shadow: 0 0 0 1px rgba(28, 25, 23, 0.06);
    }
    .steakos-brand-text {
        display: flex;
        flex-direction: column;
        gap: 0.1rem;
        min-width: 0;
        text-align: left;
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

<div class="bg-body-tertiary">
    <div class="container-lg py-4">
        <g:flashMessages />
        <g:layoutBody/>
    </div>
</div>

<footer class="border-top py-4" role="contentinfo">
    <div class="container-lg">
        <div class="d-flex flex-column flex-md-row align-items-start align-items-md-center justify-content-between gap-3">
            <div>
                <div class="fw-semibold">steakOS</div>
                <p class="text-body-secondary small mb-0">
                    <g:message code="steakos.login.footer.tagline"
                               default="Business system you run yourself — Bitcoin-ready, plain language."/>
                </p>
            </div>
            <div class="small text-body-secondary">
                <g:message code="steakos.login.footer.localeHint"
                           default="Choose your language above before signing in."/>
            </div>
        </div>
        <details class="mt-3 small text-body-secondary">
            <summary class="fw-semibold" style="cursor:pointer">
                <g:message code="steakos.login.footer.developers" default="For developers"/>
            </summary>
            <p class="mb-1 mt-2">
                <g:message code="steakos.login.footer.developersBody"
                           default="Framework docs for contributors (not required for operators):"/>
                <a href="https://grails.apache.org/docs/" target="_blank" rel="noopener">Grails User Guide</a>
            </p>
        </details>
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
