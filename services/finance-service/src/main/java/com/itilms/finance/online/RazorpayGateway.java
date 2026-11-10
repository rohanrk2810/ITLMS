package com.itilms.finance.online;

import java.math.BigDecimal;

/** What the checkout needs from Razorpay: create an order, and prove that a payment or a webhook is genuine. */
public interface RazorpayGateway {

    /** Razorpay id for the new order (order_...). */
    String createOrder(BigDecimal amount, String currency, String receipt);

    /** True when the signature is Razorpay signature over orderId|paymentId. */
    boolean validPayment(String orderId, String paymentId, String signature);

    /** True when the signature is the webhook signature over the raw request body. */
    boolean validWebhook(String body, String signature);
}
