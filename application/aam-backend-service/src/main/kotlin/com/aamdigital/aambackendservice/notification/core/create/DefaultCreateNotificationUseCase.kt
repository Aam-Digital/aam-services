package com.aamdigital.aambackendservice.notification.core.create

import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.common.error.AamErrorCode

class DefaultCreateNotificationUseCase(
    private val createNotificationHandler: List<CreateNotificationHandler>
) : CreateNotificationUseCase() {

    enum class DefaultCreateNotificationUseCaseError : AamErrorCode {
        INVALID_NOTIFICATION_CHANNEL_TYPE,
    }

    override fun apply(request: CreateNotificationRequest): UseCaseOutcome<CreateNotificationData> {

        for (handler in createNotificationHandler) {
            if (handler.canHandle(request.createUserNotificationEvent.notificationChannelType)) {
                val outcome = handler.createMessage(createUserNotificationEvent = request.createUserNotificationEvent)

                return UseCaseOutcome.Success(
                    data = outcome
                )
            }
        }

        return UseCaseOutcome.Failure(
            errorMessage = "No Handler for this NotificationChannelType",
            errorCode = DefaultCreateNotificationUseCaseError.INVALID_NOTIFICATION_CHANNEL_TYPE,
        )
    }
}
