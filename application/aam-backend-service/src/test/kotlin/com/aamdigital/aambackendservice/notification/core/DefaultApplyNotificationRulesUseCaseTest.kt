package com.aamdigital.aambackendservice.notification.core

import com.aamdigital.aambackendservice.notification.queue.UserNotificationPublisher
import com.aamdigital.aambackendservice.notification.core.trigger.DefaultApplyNotificationRulesUseCase
import com.aamdigital.aambackendservice.notification.repository.NotificationConfigRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.reset


@ExtendWith(MockitoExtension::class)
class DefaultApplyNotificationRulesUseCaseTest {

    private lateinit var service: DefaultApplyNotificationRulesUseCase

    @Mock
    lateinit var notificationConfigRepository: NotificationConfigRepository

    @Mock
    lateinit var userNotificationPublisher: UserNotificationPublisher

    @BeforeEach
    fun setUp() {
        reset(
            notificationConfigRepository,
            userNotificationPublisher
        )

        service = DefaultApplyNotificationRulesUseCase(
            notificationConfigRepository = notificationConfigRepository,
            userNotificationPublisher = userNotificationPublisher,
        )
    }


}
