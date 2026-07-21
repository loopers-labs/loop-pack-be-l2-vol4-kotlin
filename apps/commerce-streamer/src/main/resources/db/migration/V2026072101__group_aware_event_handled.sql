ALTER TABLE event_handled
    ADD COLUMN consumer_group VARCHAR(64) NULL AFTER id;

UPDATE event_handled
SET consumer_group = CASE
    WHEN event_type = 'COUPON_ISSUE_REQUESTED' THEN 'commerce-coupon-issue'
    ELSE 'loopers-default-consumer'
END
WHERE consumer_group IS NULL;

ALTER TABLE event_handled
    MODIFY COLUMN consumer_group VARCHAR(64) NOT NULL;

ALTER TABLE event_handled
    DROP INDEX uk_event_handled_event_id;

CREATE UNIQUE INDEX uk_event_handled_group_event_id
    ON event_handled (consumer_group, event_id);
