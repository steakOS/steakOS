// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package steakos.messaging

import groovy.util.logging.Slf4j

import javax.net.ssl.SSLSocketFactory
import java.nio.charset.StandardCharsets

/**
 * Minimal SMTP client (EHLO / optional AUTH LOGIN / MAIL / RCPT / DATA).
 * No Jakarta Mail dependency — enough for transactional notifies.
 */
@Slf4j
class SmtpEmailTransport implements EmailTransport {

    int connectTimeoutMs = 15000
    int soTimeoutMs = 30000

    @Override
    String deliver(Map message) {
        String host = message.host?.toString()?.trim()
        if (!host) {
            throw new IllegalArgumentException('smtp host is required')
        }
        int port = (message.port ?: MessagingKeys.DEFAULT_SMTP_PORT) as int
        String from = message.from?.toString()?.trim()
        String to = message.to?.toString()?.trim()
        if (!from || !to) {
            throw new IllegalArgumentException('from and to are required')
        }
        String subject = (message.subject ?: '').toString()
        String text = (message.text ?: '').toString()
        String username = message.username?.toString()
        String password = message.password?.toString()
        boolean startTls = message.containsKey('startTls') ? !!message.startTls : (port == 587)

        Socket socket = null
        BufferedReader reader = null
        BufferedWriter writer = null
        try {
            socket = openSocket(host, port, startTls && port == 465)
            socket.soTimeout = soTimeoutMs
            reader = new BufferedReader(new InputStreamReader(socket.inputStream, StandardCharsets.US_ASCII))
            writer = new BufferedWriter(new OutputStreamWriter(socket.outputStream, StandardCharsets.US_ASCII))

            expect(reader, 220)
            sendCmd(writer, "EHLO steakos.local")
            readMultiline(reader, 250)

            if (startTls && port != 465) {
                sendCmd(writer, 'STARTTLS')
                expect(reader, 220)
                socket = ((SSLSocketFactory) SSLSocketFactory.default).createSocket(socket, host, port, true)
                socket.soTimeout = soTimeoutMs
                reader = new BufferedReader(new InputStreamReader(socket.inputStream, StandardCharsets.US_ASCII))
                writer = new BufferedWriter(new OutputStreamWriter(socket.outputStream, StandardCharsets.US_ASCII))
                sendCmd(writer, "EHLO steakos.local")
                readMultiline(reader, 250)
            }

            if (username) {
                sendCmd(writer, 'AUTH LOGIN')
                expect(reader, 334)
                sendCmd(writer, Base64.encoder.encodeToString(username.getBytes(StandardCharsets.UTF_8)))
                expect(reader, 334)
                sendCmd(writer, Base64.encoder.encodeToString((password ?: '').getBytes(StandardCharsets.UTF_8)))
                expect(reader, 235)
            }

            sendCmd(writer, "MAIL FROM:<${from}>")
            expect(reader, 250)
            sendCmd(writer, "RCPT TO:<${to}>")
            expect(reader, 250)
            sendCmd(writer, 'DATA')
            expect(reader, 354)

            StringBuilder data = new StringBuilder()
            data.append("From: ${from}\r\n")
            data.append("To: ${to}\r\n")
            data.append("Subject: ${encodeSubject(subject)}\r\n")
            data.append("MIME-Version: 1.0\r\n")
            data.append("Content-Type: text/plain; charset=UTF-8\r\n")
            data.append("\r\n")
            data.append(text.replaceAll(/\r?\n/, '\r\n'))
            data.append("\r\n.\r\n")
            writer.write(data.toString())
            writer.flush()
            String ok = expect(reader, 250)

            sendCmd(writer, 'QUIT')
            try { expect(reader, 221) } catch (ignored) { }

            return "smtp-${System.currentTimeMillis()}"
        } finally {
            try { writer?.close() } catch (ignored) { }
            try { reader?.close() } catch (ignored) { }
            try { socket?.close() } catch (ignored) { }
        }
    }

    private Socket openSocket(String host, int port, boolean ssl) {
        if (ssl) {
            Socket s = SSLSocketFactory.default.createSocket()
            s.connect(new InetSocketAddress(host, port), connectTimeoutMs)
            return s
        }
        Socket s = new Socket()
        s.connect(new InetSocketAddress(host, port), connectTimeoutMs)
        return s
    }

    private static void sendCmd(BufferedWriter writer, String cmd) {
        writer.write(cmd)
        writer.write('\r\n')
        writer.flush()
    }

    private static String expect(BufferedReader reader, int code) {
        String line = reader.readLine()
        if (line == null) {
            throw new IOException("SMTP closed before ${code}")
        }
        if (!line.startsWith(String.valueOf(code))) {
            throw new IOException("SMTP expected ${code}, got: ${line}")
        }
        return line
    }

    private static String readMultiline(BufferedReader reader, int code) {
        String last = expect(reader, code)
        // "250-…" continuation lines until "250 "
        while (last.length() >= 4 && last.charAt(3) == '-') {
            last = reader.readLine()
            if (last == null) break
        }
        return last
    }

    private static String encodeSubject(String subject) {
        // Keep ASCII subjects plain; otherwise UTF-8 B-encoding
        boolean ascii = true
        for (int i = 0; i < subject.length(); i++) {
            if (subject.charAt(i) > 127) {
                ascii = false
                break
            }
        }
        if (ascii) return subject
        String b64 = Base64.encoder.encodeToString(subject.getBytes(StandardCharsets.UTF_8))
        return "=?UTF-8?B?${b64}?="
    }
}
