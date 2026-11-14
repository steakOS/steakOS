// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
package steakos.payments

/** One accepted BTCPay delivery. A second row with the same id is a no-op. */
class WebhookDelivery {
    String deliveryId

    static constraints = {
        deliveryId nullable: false, blank: false, maxSize: 200, unique: true
    }

    static mapping = {
        table 'webhook_delivery'
    }
}
