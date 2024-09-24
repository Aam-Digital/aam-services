-- ----------------------------------------------------------------------------------- --
-- Will create the SYNC_ENTITY Table, needed for internal state handling and caching   --
-- ----------------------------------------------------------------------------------- --
CREATE TABLE IF NOT EXISTS SYNC_ENTRY
(
    ID         SERIAL PRIMARY KEY,
    DATABASE   VARCHAR(255),
    LATEST_REF VARCHAR(1000)
);
