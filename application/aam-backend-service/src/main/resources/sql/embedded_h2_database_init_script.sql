-- ----------------------------------------------------------------------------------- --
-- Will create the SYNC_ENTITY Table, needed for internal state handling and caching   --
-- ----------------------------------------------------------------------------------- --
CREATE TABLE IF NOT EXISTS SYNC_ENTRY
(
    ID         SERIAL PRIMARY KEY,
    DATABASE   TEXT(1000),
    LATEST_REF TEXT(1000)
);
