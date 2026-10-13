<html>
<head>
	<meta name="layout" content="${gspLayout ?: 'main'}"/>
	<title><g:message code='springSecurity.login.title'/></title>
	<style type="text/css" media="screen">
		/* Spring Security Core plugin default login styles */
		#login {
			margin: 15px 0px;
			padding: 0px;
			text-align: center;
			font-family: Arial, Helvetica, sans-serif;
		}

		#login .inner {
			width: 340px;
			padding-bottom: 6px;
			margin: 60px auto;
			text-align: left;
			border: 1px solid #aab;
			background-color: #f0f0fa;
			-moz-box-shadow: 2px 2px 2px #eee;
			-webkit-box-shadow: 2px 2px 2px #eee;
			-khtml-box-shadow: 2px 2px 2px #eee;
			box-shadow: 2px 2px 2px #eee;
		}

		#login .inner .fheader {
			padding: 18px 26px 14px 26px;
			background-color: #f7f7ff;
			margin: 0px 0 14px 0;
			color: #2e3741;
			font-size: 18px;
			font-weight: bold;
		}

		#login .inner .cssform p {
			clear: left;
			margin: 0;
			padding: 4px 0 3px 0;
			padding-left: 105px;
			margin-bottom: 20px;
			height: 1%;
		}

		#login .inner .cssform input[type="text"] {
			width: 75%;
		}

		#login .inner .cssform label {
			font-weight: bold;
			float: left;
			text-align: right;
			margin-left: -105px;
			width: 110px;
			padding-top: 3px;
			padding-right: 10px;
		}

		#remember_me_holder {
			padding-left: 120px;
		}

		#submit {
			margin-left: 15px;
		}

		#remember_me_holder label {
			float: none;
			margin-left: 0;
			text-align: left;
			width: 200px;
		}

		#login .inner .login_message {
			padding: 6px 25px 20px 25px;
			color: #c33;
		}

		#login .inner .text_ {
			width: 75%;
		}

		#login .inner .chk {
			height: 12px;
		}

		#passwordToggler {
			padding: 3px 4px;
			cursor: pointer;
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
				<select id="ui_locale" class="text_" name="ui_locale" style="width:75%">
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
				<input type="password" class="text_" name="${passwordParameter ?: 'password'}" id="password"/>
				<i id="passwordToggler" title="${message(code: 'steakos.login.password.toggle')}" onclick="passwordDisplayToggle()">&#128065;</i>
			</p>

			<p id="remember_me_holder">
				<input type="checkbox" class="chk" name="${rememberMeParameter ?: 'remember-me'}" id="remember_me" <g:if test='${hasCookie}'>checked="checked"</g:if>/>
				<label for="remember_me"><g:message code='springSecurity.login.remember.me.label'/></label>
			</p>

			<p>
				<input type="submit" id="submit" value="${message(code: 'springSecurity.login.button')}"/>
			</p>
		</form>

		<div class="nostr-login" style="padding: 0 26px 18px 26px; border-top: 1px solid #ccd; margin-top: 8px;">
			<p style="font-size: 13px; color: #445; margin: 14px 0 8px 0;">
				<g:message code='steakos.login.nostr.intro'/>
			</p>
			<button type="button" id="nostrSignInBtn" style="width: 100%; padding: 8px;">
				<g:message code='steakos.login.nostr.button'/>
			</button>
			<p id="nostrLoginMsg" style="font-size: 12px; color: #c33; min-height: 1.2em; margin: 8px 0 0 0;"></p>
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
