package com.aamdigital.aambackendservice.notification.core.outbox

import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.common.outbox.OutboxDeliveryResult
import com.aamdigital.aambackendservice.common.outbox.OutboxHandler
import com.aamdigital.aambackendservice.notification.core.CreateUserNotificationEvent
import com.aamdigital.aambackendservice.notification.core.create.CreateNotificationRequest
import com.aamdigital.aambackendservice.notification.core.create.CreateNotificationUseCase
import com.aamdigital.aambackendservice.notification.core.create.TransientNotificationException

/**
 * Delivers a notification from the notification outbox by passing it to [CreateNotificationUseCase].
 *
 * Only a [TransientNotificationException] (an SMTP connection timeout, say) is worth retrying; any
 * other failure, such as no handler for the channel, would fail the same way again.
 */
class NotificationOutboxHandler(
    private val createNotificationUseCase: CreateNotificationUseCase
) : OutboxHandler<CreateUserNotificationEvent> {
    override fun deliver(payload: CreateUserNotificationEvent): OutboxDeliveryResult =
        try {
            when (val outcome = createNotificationUseCase.run(CreateNotificationRequest(payload))) {
                is UseCaseOutcome.Success -> {
                    OutboxDeliveryResult.Delivered
                }

                is UseCaseOutcome.Failure -> {
                    OutboxDeliveryResult.Rejected("[${outcome.errorCode}] ${outcome.errorMessage}", outcome.cause)
                }
            }
        } catch (ex: TransientNotificationException) {
            OutboxDeliveryResult.RetryLater(ex.localizedMessage, ex)
        }
}
