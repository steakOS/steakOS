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
     * Public processors must be https. A private or http address is accepted only
     * when its host is on the named allowlist. Redirects are not followed by the client.
     */
    static String validateAndNormalize(String raw, Collection<String> internalHosts = []) {
        if (!raw?.trim()) {
            throw new PaymentProviderException('btcpay.baseUrl is required', 'CONFIG')
        }
        URI uri
        try {
            uri = URI.create(raw.trim())
        } catch (Exception e) {
            throw new PaymentProviderException('invalid btcpay.baseUrl', 'CONFIG')
        }
        String scheme = uri.scheme?.toLowerCase()
        if (!(scheme in ['https', 'http'])) {
            throw new PaymentProviderException('btcpay.baseUrl must be http or https', 'CONFIG')
        }
        String host = uri.host
        if (!host) {
            throw new PaymentProviderException('btcpay.baseUrl missing host', 'CONFIG')
        }
        boolean listed = internalHosts?.any { String allowed -> allowed?.equalsIgnoreCase(host) }
        if (!listed) {
            if (scheme != 'https') {
                throw new PaymentProviderException('btcpay.baseUrl must use https', 'CONFIG')
            }
            if (isBlockedHost(host) || resolvesToBlockedAddress(host)) {
                throw new PaymentProviderException('btcpay.baseUrl host not allowed', 'SSRF')
            }
        }
        String path = uri.path ?: ''
        if (path.endsWith('/')) {
            path = path.length() > 1 ? path[0..-2] : ''
        }
        String base = "${scheme}://${host}"
        if (uri.port > 0) {
            boolean defaultPort = (scheme == 'https' && uri.port == 443) || (scheme == 'http' && uri.port == 80)
            if (!defaultPort || rawContainsExplicitPort(raw, uri.port)) {
                base += ":${uri.port}"
            }
        }
        base += path
        return base
    }

    /** Test hook. Production uses DNS. */
    static Closure<List<InetAddress>> addressResolver = { String host ->
        InetAddress.getAllByName(host).toList()
    }

    private static boolean rawContainsExplicitPort(String raw, int port) {
        return raw.contains(":${port}")
    }

    static boolean resolvesToBlockedAddress(String host) {
        if (isLiteralAddress(host)) return false
        try {
            List<InetAddress> addresses = addressResolver.call(host) ?: []
            if (addresses.isEmpty()) return true
            return addresses.any { InetAddress address -> isBlockedAddress(address) }
        } catch (Exception ignored) {
            return true
        }
    }

    private static boolean isLiteralAddress(String host) {
        return host ==~ /^[\d.]+$/ || host.contains(':')
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
        if (h ==~ /^100\.(6[4-9]|[7-9]\d|1[0-1]\d|12[0-7])\.\d+\.\d+$/) return true
        if (h ==~ /^0\.\d+\.\d+\.\d+$/) return true
        if (h ==~ /^198\.1[89]\.\d+\.\d+$/) return true
        if (h.startsWith('[') && (h.contains('fd') || h.contains('fe80') || h.contains('ffff:127') || h.contains('ffff:10.'))) {
            return true
        }
        return false
    }

    static boolean isBlockedAddress(InetAddress address) {
        if (address == null) return true
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true
        }
        return isBlockedHost(address.hostAddress)
    }
}
