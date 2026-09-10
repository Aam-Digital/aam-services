package com.aamdigital.aambackendservice.skill.core

import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.common.queue.core.QueueMessage
import com.aamdigital.aambackendservice.skill.core.event.UserProfileUpdateEvent
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * In-process [UserProfileUpdatePublisher]: hands the event straight to [SyncUserProfileUseCase]
 * instead of routing it through the `skill.userProfile.update` queue, which was a handoff between
 * two objects in the same JVM.
 *
 * A failed sync is logged and skipped, never retried. That is precisely what the queue did: its
 * consumer called `SyncUserProfileUseCase.run`, which converts every exception into a
 * `UseCaseOutcome.Failure` (already WARN-logged by `DomainUseCase`), then ignored the result and
 * acked the message - so there was no retry, no dead letter queue, and the caller's sync cursor
 * advanced regardless. Propagating the failure instead would abort the whole fetch and hold the
 * cursor back, turning one bad profile into a stalled project-wide sync with nowhere to park it.
 * Recovery stays what it is today: the next scheduled SkillLab re-sync.
 *
 * [channel] is ignored; it survives only because the interface still carries the queue-shaped
 * signature that is retired together with `QueueMessage`.
 */
class InProcessUserProfileUpdatePublisher(
    private val syncUserProfileUseCase: SyncUserProfileUseCase
) : UserProfileUpdatePublisher {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun publish(
        channel: String,
        event: UserProfileUpdateEvent
    ): QueueMessage {
        logger.trace("[InProcessUserProfileUpdatePublisher]: handle {} for channel '{}'", event, channel)

        val outcome =
            syncUserProfileUseCase.run(
                SyncUserProfileRequest(
                    userProfile = DomainReference(event.userProfileId),
                    project = DomainReference(event.projectId)
                )
            )

        if (outcome is UseCaseOutcome.Failure) {
            logger.warn(
                "[{}] could not sync user profile {} of project {}: {}",
                outcome.errorCode,
                event.userProfileId,
                event.projectId,
                outcome.errorMessage,
                outcome.cause
            )
        }

        return QueueMessage(
            id = UUID.randomUUID(),
            eventType = UserProfileUpdateEvent::class.java.canonicalName,
            event = event,
            createdAt =
                Instant
                    .now()
                    .atOffset(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        )
    }
}
