<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1"/>
    <title><g:layoutTitle default="steakOS"/></title>
    <asset:link rel="icon" href="favicon.ico" type="image/x-ico"/>
    <asset:stylesheet src="application.css"/>
    <g:layoutHead/>
</head>

<body>

<nav class="navbar navbar-expand-lg bg-body border-bottom shadow-sm">
    <div class="container-lg">
        <a class="navbar-brand d-flex align-items-center" href="${request.contextPath}/">
            <asset:image class="w-75" src="steakos.svg" alt="steakOS"/>
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
                <a class="btn btn-sm btn-primary" href="${request.contextPath}/login/auth">Login</a>
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
