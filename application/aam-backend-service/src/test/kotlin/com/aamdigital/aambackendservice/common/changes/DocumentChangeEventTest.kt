package com.aamdigital.aambackendservice.common.changes

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DocumentChangeEventTest {
    private fun event(
        documentId: String = "Child:1",
        rev: String = "1-abc",
        deleted: Boolean = false
    ) = DocumentChangeEvent(
        database = "app",
        documentId = documentId,
        rev = rev,
        currentVersion = emptyMap<String, Any>(),
        previousVersion = emptyMap<String, Any>(),
        deleted = deleted
    )

    @Test
    fun `should take the entity type from the document id prefix`() {
        assertThat(event(documentId = "Child:1").entityType).isEqualTo("Child")
        assertThat(event(documentId = "ReportConfig:with:colons").entityType).isEqualTo("ReportConfig")
        assertThat(event(documentId = "no-prefix").entityType).isEqualTo("no-prefix")
    }

    @Test
    fun `should tell a created from an updated document by the revision generation`() {
        assertThat(event(rev = "1-abc").changeType).isEqualTo("created")
        assertThat(event(rev = "2-abc").changeType).isEqualTo("updated")
        assertThat(event(rev = "not-a-revision").changeType).isEqualTo("created")
    }

    @Test
    fun `should report a deleted document as deleted whatever its revision`() {
        assertThat(event(rev = "3-abc", deleted = true).changeType).isEqualTo("deleted")
    }
}
