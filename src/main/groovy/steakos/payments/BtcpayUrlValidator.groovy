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

package steakos.payments

/**
 * SSRF guard for user-supplied BTCPay base URLs (OI-F01 / Phase 1).
 */
final class BtcpayUrlValidator {

    private BtcpayUrlValidator() {}

    /**
     * @param allowPrivate when true (dev), allow localhost / private ranges
     * @return normalized base URL without trailing slash
     */
    static String validateAndNormalize(String raw, boolean allowPrivate = false) {
        if (!raw?.trim()) {
            throw new PaymentProviderException('btcpay.baseUrl is required', 'CONFIG')
        }
        URI uri
        try {
            uri = URI.create(raw.trim())
        } catch (Exception e) {
            throw new PaymentProviderException("invalid btcpay.baseUrl: ${e.message}", 'CONFIG')
        }
        String scheme = uri.scheme?.toLowerCase()
        if (!(scheme in ['https', 'http'])) {
            throw new PaymentProviderException('btcpay.baseUrl must be http or https', 'CONFIG')
        }
        if (scheme == 'http' && !allowPrivate) {
            throw new PaymentProviderException('btcpay.baseUrl must use https outside dev', 'CONFIG')
        }
        String host = uri.host
        if (!host) {
            throw new PaymentProviderException('btcpay.baseUrl missing host', 'CONFIG')
        }
        if (!allowPrivate && isBlockedHost(host)) {
            throw new PaymentProviderException("btcpay.baseUrl host not allowed: ${host}", 'SSRF')
        }
        String path = uri.path ?: ''
        if (path.endsWith('/')) {
            path = path.length() > 1 ? path[0..-2] : ''
        }
        String base = "${scheme}://${host}"
        if (uri.port > 0 && uri.port != 80 && uri.port != 443) {
            base += ":${uri.port}"
        }
        base += path
        return base
    }

    static boolean isBlockedHost(String host) {
        String h = host.toLowerCase()
        if (h == 'localhost' || h.endsWith('.localhost') || h == 'metadata.google.internal') {
            return true
        }
        if (h == '0.0.0.0' || h == '::1' || h == '[::1]') {
            return true
        }
        // IPv4 private / link-local / loopback
        if (h ==~ /^127\.\d+\.\d+\.\d+$/) return true
        if (h ==~ /^10\.\d+\.\d+\.\d+$/) return true
        if (h ==~ /^192\.168\.\d+\.\d+$/) return true
        if (h ==~ /^172\.(1[6-9]|2\d|3[0-1])\.\d+\.\d+$/) return true
        if (h ==~ /^169\.254\.\d+\.\d+$/) return true
        return false
    }
}
