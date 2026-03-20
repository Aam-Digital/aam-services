package com.aamdigital.aambackendservice.common.changes

class NoopDatabaseChangeDetection : DatabaseChangeDetection {
    override fun checkForChanges() {}
}
