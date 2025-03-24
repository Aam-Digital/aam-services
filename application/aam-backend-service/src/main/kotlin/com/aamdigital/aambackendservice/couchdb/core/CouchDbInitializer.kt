package com.aamdigital.aambackendservice.couchdb.core

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean


/**
 * Representing a couchdb database
 */
open class DatabaseRequest(
    val name: String
)

/**
 * Service to initialize the couchdb instance.
 * Will create all databases needed. Can be extended by feature modules.
 * If a feature is getting enabled later on, the database is created then.
 *
 * No databases are deleted by disabling features.
 *
 */
class CouchDbInitializer(
    private val couchDbClient: CouchDbClient,
    private val databaseRequests: List<DatabaseRequest>
) : InitializingBean {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private val DEFAULT_DATABASES = listOf(
            DatabaseRequest("app"),
            DatabaseRequest("app-attachments"),
        )
    }

    private fun initCouchDb() {
        DEFAULT_DATABASES.forEach {
            createDatabase(it)
        }

        databaseRequests.forEach {
            createDatabase(it)
        }
    }

    private fun createDatabase(databaseRequest: DatabaseRequest) {
        val dbExists = try {
            couchDbClient.databaseExists(databaseRequest.name)
        } catch (e: Exception) {
            logger.error("Error fetching status of database ${databaseRequest.name}", e)
            return
        }

        if (!dbExists) {
            logger.info("Database ${databaseRequest.name} does not exist. Creating it.")
            try {
                couchDbClient.createDatabase(databaseRequest.name)
                logger.info("Database ${databaseRequest.name} created successfully.")
            } catch (e: Exception) {
                logger.error("Error creating database ${databaseRequest.name}", e)
            }
        } else {
            logger.info("Database ${databaseRequest.name} already exists.")
        }
    }

    override fun afterPropertiesSet() {
        initCouchDb()
    }
}
