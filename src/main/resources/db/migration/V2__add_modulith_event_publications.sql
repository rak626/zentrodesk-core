create table event_publication (
    id uuid primary key,
    publication_date timestamp(6) with time zone not null,
    listener_id varchar(255) not null,
    serialized_event varchar(255) not null,
    event_type varchar(255) not null,
    completion_date timestamp(6) with time zone,
    last_resubmission_date timestamp(6) with time zone,
    completion_attempts integer not null,
    status varchar(255)
);

create table event_publication_archive (
    id uuid primary key,
    publication_date timestamp(6) with time zone not null,
    listener_id varchar(255) not null,
    serialized_event varchar(255) not null,
    event_type varchar(255) not null,
    completion_date timestamp(6) with time zone,
    last_resubmission_date timestamp(6) with time zone,
    completion_attempts integer not null,
    status varchar(255)
);

create index idx_event_publication_listener on event_publication(listener_id);
create index idx_event_publication_completion on event_publication(completion_date);
create index idx_event_publication_status on event_publication(status);
create index idx_event_publication_archive_completion on event_publication_archive(completion_date);
