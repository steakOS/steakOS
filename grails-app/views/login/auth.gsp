<html>
<head>
	<meta name="layout" content="${gspLayout ?: 'main'}"/>
	<title><g:message code='springSecurity.login.title'/></title>
	<style type="text/css" media="screen">
		/* Match SPA tokens (styles.css) — modal card + form-grid inputs */
		#login {
			--steak-orange: #e85d04;
			--steak-orange-soft: #f48c06;
			--steak-ink: #1a1a1a;
			--steak-muted: #5c5c5c;
			--steak-bg: #faf8f6;
			--steak-card: #ffffff;
			--steak-border: #ebe4dc;
			--radius: 14px;
			--shadow: 0 8px 30px rgba(28, 25, 23, 0.06);
			margin: 0;
			padding: 0.5rem 0 1.5rem;
			text-align: center;
			font-family: system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
			color: var(--steak-ink);
		}

		#login .inner {
			width: min(440px, calc(100vw - 32px));
			margin: 1.25rem auto 0;
			text-align: left;
			background: var(--steak-card);
			border: 1px solid var(--steak-border);
			border-radius: var(--radius);
			box-shadow: var(--shadow), 0 20px 50px rgba(232, 93, 4, 0.08);
			overflow: hidden;
		}

		#login .inner .fheader {
			padding: 1.15rem 1.35rem 0.95rem;
			margin: 0;
			color: var(--steak-ink);
			font-size: 1.15rem;
			font-weight: 800;
			letter-spacing: -0.02em;
			border-bottom: 1px solid var(--steak-border);
			background: linear-gradient(180deg, #fff 0%, #fffaf6 100%);
		}

		#login .inner .cssform {
			padding: 1.15rem 1.35rem 0.35rem;
			display: grid;
			gap: 0.95rem;
		}

		#login .inner .cssform p {
			margin: 0;
			padding: 0;
			display: grid;
			gap: 0.35rem;
		}

		#login .inner .cssform label {
			font-weight: 600;
			font-size: 0.88rem;
			color: var(--steak-ink);
			text-align: left;
			float: none;
			width: auto;
			margin: 0;
			padding: 0;
			white-space: normal;
		}

		#login .inner .cssform input[type="text"],
		#login .inner .cssform input[type="password"],
		#login .inner .cssform select.text_ {
			width: 100%;
			box-sizing: border-box;
			border: 1px solid var(--steak-border);
			border-radius: 10px;
			padding: 0.65rem 0.8rem;
			background: #fff;
			color: var(--steak-ink);
			font: inherit;
			font-size: 0.95rem;
			outline: none;
			transition: border-color 0.15s ease, box-shadow 0.15s ease;
		}

		#login .inner .cssform input[type="text"]:focus,
		#login .inner .cssform input[type="password"]:focus,
		#login .inner .cssform select.text_:focus {
			border-color: #fdba74;
			box-shadow: 0 0 0 3px rgba(232, 93, 4, 0.15);
		}

		#login .password-row {
			display: grid;
			grid-template-columns: 1fr auto;
			gap: 0.4rem;
			align-items: stretch;
		}

		#login .password-row input {
			width: 100%;
		}

		#passwordToggler {
			display: inline-flex;
			align-items: center;
			justify-content: center;
			min-width: 2.75rem;
			padding: 0 0.65rem;
			border: 1px solid var(--steak-border);
			border-radius: 10px;
			background: #fff7ed;
			color: #c2410c;
			cursor: pointer;
			font-style: normal;
			line-height: 1;
		}

		#passwordToggler:hover {
			background: #ffedd5;
			border-color: #fed7aa;
		}

		#remember_me_holder {
			display: flex !important;
			flex-direction: row !important;
			align-items: center;
			gap: 0.55rem;
			padding-top: 0.15rem;
		}

		#remember_me_holder label {
			font-weight: 600;
			margin: 0;
			cursor: pointer;
		}

		#login .inner .chk {
			width: 1.05rem;
			height: 1.05rem;
			accent-color: var(--steak-orange);
			margin: 0;
		}

		#login .actions {
			padding-top: 0.25rem;
		}

		#submit {
			display: inline-flex;
			align-items: center;
			justify-content: center;
			width: 100%;
			margin: 0;
			border: 1px solid var(--steak-orange);
			border-radius: 999px;
			padding: 0.7rem 1.1rem;
			background: var(--steak-orange);
			color: #fff;
			font-weight: 700;
			font-size: 0.95rem;
			cursor: pointer;
			transition: background 0.15s ease, border-color 0.15s ease;
		}

		#submit:hover {
			background: #d00000;
			border-color: #d00000;
		}

		#login .inner .login_message {
			margin: 0.85rem 1.35rem 0;
			padding: 0.65rem 0.85rem;
			border-radius: 10px;
			background: #fef2f2;
			border: 1px solid #fecaca;
			color: #991b1b;
			font-size: 0.9rem;
			font-weight: 600;
		}

		#login .nostr-login {
			padding: 0.85rem 1.35rem 1.25rem;
			border-top: 1px solid var(--steak-border);
			margin-top: 0.5rem;
		}

		#login .nostr-login .or-label {
			font-size: 0.72rem;
			color: var(--steak-muted);
			margin: 0 0 0.55rem;
			text-transform: uppercase;
			letter-spacing: 0.06em;
			font-weight: 700;
		}

		#login .btn-secondary-login {
			display: inline-flex;
			align-items: center;
			justify-content: center;
			width: 100%;
			padding: 0.65rem 1rem;
			background: #fff7ed;
			border: 1px solid #fed7aa;
			border-radius: 999px;
			color: #c2410c;
			font-weight: 600;
			font-size: 0.9rem;
			cursor: pointer;
			transition: background 0.15s ease, border-color 0.15s ease;
		}

		#login .btn-secondary-login:hover {
			background: #ffedd5;
			border-color: #fdba74;
		}

		#nostrLoginMsg {
			font-size: 0.8rem;
			color: #991b1b;
			min-height: 1.2em;
			margin: 0.55rem 0 0;
		}
	</style>
</head>

<body>
<div id="login">
	<div class="inner">
		<div class="fheader"><g:message code='springSecurity.login.header'/></div>

		<g:if test='${flash.message}'>
			<div class="login_message">${flash.message}</div>
		</g:if>

		<form action="${postUrl ?: '/login/authenticate'}" method="POST" id="loginForm" class="cssform" autocomplete="off">
			<g:if test="${_csrf}">
				<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
			</g:if>
			<p>
				<label for="ui_locale"><g:message code='steakos.login.language.label'/>:</label>
				<select id="ui_locale" class="text_" name="ui_locale">
					<option value="en_US">English (United States)</option>
					<option value="en_SG">English (Singapore)</option>
					<option value="de_DE">Deutsch (Deutschland)</option>
					<option value="de_AT">Deutsch (Österreich)</option>
					<option value="de_CH">Deutsch (Schweiz)</option>
					<option value="es_ES">Español (España)</option>
				</select>
			</p>
			<p>
				<label for="username"><g:message code='springSecurity.login.username.label'/>:</label>
				<input type="text" class="text_" name="${usernameParameter ?: 'username'}" id="username" autocapitalize="none"/>
			</p>

			<p>
				<label for="password"><g:message code='springSecurity.login.password.label'/>:</label>
				<span class="password-row">
					<input type="password" class="text_" name="${passwordParameter ?: 'password'}" id="password"/>
					<i id="passwordToggler" title="${message(code: 'steakos.login.password.toggle')}" onclick="passwordDisplayToggle()" role="button" tabindex="0">&#128065;</i>
				</span>
			</p>

			<p id="remember_me_holder">
				<input type="checkbox" class="chk" name="${rememberMeParameter ?: 'remember-me'}" id="remember_me" <g:if test='${hasCookie}'>checked="checked"</g:if>/>
				<label for="remember_me"><g:message code='springSecurity.login.remember.me.label'/></label>
			</p>

			<p class="actions">
				<input type="submit" id="submit" value="${message(code: 'springSecurity.login.button')}"/>
			</p>
		</form>

		<div class="nostr-login">
			<p class="or-label">
				<g:message code='steakos.login.nostr.secondary' default="Or"/>
			</p>
			<button type="button" id="nostrSignInBtn" class="btn-secondary-login"
				title="${message(code: 'steakos.login.nostr.buttonTitle')}"
				aria-label="${message(code: 'steakos.login.nostr.button')} — ${message(code: 'steakos.login.nostr.buttonTitle')}">
				<g:message code='steakos.login.nostr.button'/>
			</button>
			<p id="nostrLoginMsg"></p>
		</div>
		<div id="steakos-login-i18n" hidden
			data-waiting="${message(code: 'steakos.login.nostr.waiting')}"
			data-no-extension="${message(code: 'steakos.login.nostr.noExtension')}"
			data-challenge-failed="${message(code: 'steakos.login.nostr.challengeFailed')}"
			data-verify-failed="${message(code: 'steakos.login.nostr.verifyFailed')}"></div>
	</div>
</div>
<script type="text/javascript">
	var STEAKOS_LOGIN_MSG = {
		waiting: 'Waiting for extension…',
		noExtension: 'No browser extension found.',
		challengeFailed: 'Could not start extension login',
		verifyFailed: 'Extension login failed'
	};

	document.addEventListener("DOMContentLoaded", function () {
		var i18nEl = document.getElementById('steakos-login-i18n');
		if (i18nEl && i18nEl.dataset) {
			STEAKOS_LOGIN_MSG.waiting = i18nEl.dataset.waiting || STEAKOS_LOGIN_MSG.waiting;
			STEAKOS_LOGIN_MSG.noExtension = i18nEl.dataset.noExtension || STEAKOS_LOGIN_MSG.noExtension;
			STEAKOS_LOGIN_MSG.challengeFailed = i18nEl.dataset.challengeFailed || STEAKOS_LOGIN_MSG.challengeFailed;
			STEAKOS_LOGIN_MSG.verifyFailed = i18nEl.dataset.verifyFailed || STEAKOS_LOGIN_MSG.verifyFailed;
		}

		var sel = document.getElementById('ui_locale');
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
		function pickLocale(code) {
			if (!sel || !code) return false;
			var c = String(code).replace('-', '_');
			for (var i = 0; i < sel.options.length; i++) {
				var v = sel.options[i].value;
				if (v === c || v.indexOf(c + '_') === 0 || c.indexOf(v) === 0) {
					sel.selectedIndex = i;
					return true;
				}
			}
			// Short lang → first matching region (es → es_ES)
			var short = c.split('_')[0];
			for (var j = 0; j < sel.options.length; j++) {
				if (sel.options[j].value.indexOf(short + '_') === 0) {
					sel.selectedIndex = j;
					return true;
				}
			}
			return false;
		}
		function syncLocaleCookies(fullCode) {
			var v = fullCode || 'en_US';
			var shortLang = v.split(/[_-]/)[0];
			try { localStorage.setItem(key, v); } catch (e) {}
			// Shared with SPA (and survives logout). Never clear these on sign-out.
			try {
				document.cookie = 'STEAKOS_LANG=' + encodeURIComponent(shortLang) + '; path=/; SameSite=Lax; max-age=31536000';
				document.cookie = 'STEAKOS_UI_LOCALE=' + encodeURIComponent(v) + '; path=/; SameSite=Lax; max-age=31536000';
			} catch (e) {}
			return shortLang;
		}
		// Restore order: URL → full cookie → short cookie → localStorage.
		// Must run BEFORE syncLocaleCookies so we do not overwrite a saved locale with en_US.
		try {
			var qs = new URLSearchParams(window.location.search);
			var langParam = qs.get('locale') || qs.get('lang');
			var stored = null;
			try { stored = localStorage.getItem(key); } catch (e) {}
			var fromUiCookie = readCookie('STEAKOS_UI_LOCALE');
			var fromLangCookie = readCookie('STEAKOS_LANG');
			if (langParam) pickLocale(langParam);
			else if (fromUiCookie) pickLocale(fromUiCookie);
			else if (fromLangCookie) pickLocale(fromLangCookie);
			else if (stored) pickLocale(stored);
		} catch (e) {}
		if (sel) {
			sel.addEventListener('change', function () {
				var v = sel.value;
				var shortLang = syncLocaleCookies(v);
				var url = new URL(window.location.href);
				url.searchParams.set('lang', v);
				url.searchParams.set('locale', v);
				window.location.href = url.toString();
			});
			syncLocaleCookies(sel.value);
			// Before password POST, refresh cookies from the current selection.
			var form = document.forms['loginForm'];
			if (form) {
				form.addEventListener('submit', function () {
					syncLocaleCookies(sel.value);
				});
			}
		}
		document.forms['loginForm'].elements['username'].focus();

		var nostrBtn = document.getElementById('nostrSignInBtn');
		var nostrMsg = document.getElementById('nostrLoginMsg');
		if (nostrBtn) {
			nostrBtn.addEventListener('click', function () {
				nostrMsg.style.color = '#445';
				nostrMsg.textContent = STEAKOS_LOGIN_MSG.waiting;
				nostrSignIn().catch(function (err) {
					nostrMsg.style.color = '#c33';
					nostrMsg.textContent = err && err.message ? err.message : String(err);
				});
			});
		}
	});

	function passwordDisplayToggle() {
		var toggleEl = document.getElementById("passwordToggler");
		var eyeIcon = '\u{1F441}';
		var xIcon = '\u{2715}';
		var passEl = document.getElementById("password");

		if (passEl.type === "password") {
			toggleEl.innerHTML = xIcon;
			passEl.type = "text";
		} else {
			toggleEl.innerHTML = eyeIcon;
			passEl.type = "password";
		}
	}

	/** NIP-07 — challenge → signEvent → session (AUTH concept A3–A5). */
	async function nostrSignIn() {
		if (!window.nostr || typeof window.nostr.signEvent !== 'function') {
			throw new Error(STEAKOS_LOGIN_MSG.noExtension);
		}
		var chRes = await fetch('/api/auth/nostr/challenge', {
			method: 'POST',
			headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
			credentials: 'same-origin',
			body: '{}'
		});
		var chBody = await chRes.json().catch(function () { return {}; });
		if (!chRes.ok || !chBody.challenge) {
			throw new Error(chBody.error || STEAKOS_LOGIN_MSG.challengeFailed);
		}
		var event = {
			kind: 22242,
			created_at: Math.floor(Date.now() / 1000),
			tags: [['challenge', chBody.challenge]],
			content: 'steakOS login'
		};
		var signed = await window.nostr.signEvent(event);
		var vRes = await fetch('/api/auth/nostr/verify', {
			method: 'POST',
			headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
			credentials: 'same-origin',
			body: JSON.stringify({ challenge: chBody.challenge, event: signed })
		});
		var vBody = await vRes.json().catch(function () { return {}; });
		if (!vRes.ok) {
			throw new Error(vBody.error || STEAKOS_LOGIN_MSG.verifyFailed);
		}
		window.location.href = '/app';
	}
</script>
</body>
</html>
