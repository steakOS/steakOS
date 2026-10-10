// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors

/**
 * Guidance for setting up a TOTP authenticator before / during 2FA login.
 * Keep copy plain — first-time operators often have never used an auth app.
 */
export default function AuthAppGuide({ compact = false, title }) {
  return (
    <div
      className="auth-app-guide"
      style={{
        marginTop: compact ? 8 : 12,
        marginBottom: compact ? 8 : 12,
        padding: compact ? '10px 12px' : '12px 14px',
        borderRadius: 8,
        border: '1px solid var(--steak-border, #ddd)',
        background: 'var(--surface-muted, #faf8f6)',
        fontSize: compact ? '0.9rem' : '0.95rem',
        lineHeight: 1.45,
      }}
    >
      <p style={{ marginTop: 0, fontWeight: 600 }}>
        {title || 'Before you sign in: set up an authenticator app'}
      </p>
      <p className="muted" style={{ marginTop: 0 }}>
        steakOS asks for a 6-digit code from an app on your phone (two-factor authentication). Install
        one app <strong>before</strong> you leave setup or open the login page, then scan the QR
        (or type the key) when steakOS shows it.
      </p>
      <ol style={{ margin: '0.5rem 0', paddingLeft: '1.25rem' }}>
        <li>Install an authenticator app on your phone (suggestions below).</li>
        <li>
          When steakOS shows a QR code, open the app → <strong>Add account</strong> / scan QR (or
          enter the secret key manually).
        </li>
        <li>Enter the 6-digit code that appears in the app to confirm.</li>
        <li>
          <strong>Save the recovery codes</strong> steakOS shows — store them offline. You need them
          if you lose the phone.
        </li>
      </ol>
      <p style={{ marginBottom: 4, fontWeight: 600 }}>Suggested apps</p>
      <ul style={{ margin: '0 0 0.5rem', paddingLeft: '1.25rem' }}>
        <li>
          <strong>Microsoft Authenticator</strong> — iOS &amp; Android (App Store / Play Store)
        </li>
        <li>
          <strong>Google Authenticator</strong> — iOS &amp; Android
        </li>
        <li>
          <strong>Aegis Authenticator</strong> — recommended FOSS on <strong>Android</strong>{' '}
          (F-Droid or Play Store)
        </li>
        <li>
          <strong>2FAS Auth</strong> — recommended FOSS on <strong>iOS</strong> &amp; Android
          (open-source, free)
        </li>
      </ul>
      <p className="muted" style={{ marginBottom: 0, fontSize: '0.85rem' }}>
        Any standard TOTP app works. Prefer apps you control (Aegis / 2FAS) if you want open-source
        options. Do not share codes or recovery codes with anyone.
      </p>
    </div>
  )
}
