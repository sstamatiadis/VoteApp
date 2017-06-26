# --- Created by Ebean DDL
# To stop Ebean DDL generation, remove this comment and start using Evolutions

# --- !Ups

create table option (
  id                            bigserial not null,
  code                          varchar(255) not null,
  poll_id                       bigint,
  option                        varchar(100) not null,
  votes                         integer not null,
  time_updated                  timestamp not null,
  time_created                  timestamp not null,
  constraint pk_option primary key (id)
);

create table participation (
  voter_id                      bigint not null,
  poll_id                       bigint not null,
  code                          varchar(255) not null,
  time_updated                  timestamp not null,
  time_created                  timestamp not null,
  constraint pk_participation primary key (voter_id,poll_id)
);

create table poll (
  id                            bigserial not null,
  code                          varchar(255) not null,
  status                        varchar(255) not null,
  creator_id                    bigint not null,
  visibility                    varchar(255) not null,
  mode                          varchar(255) not null,
  question                      varchar(200) not null,
  expiration                    timestamp not null,
  time_updated                  timestamp not null,
  time_created                  timestamp not null,
  constraint pk_poll primary key (id)
);

create table vote (
  voter_id                      bigint not null,
  poll_id                       bigint not null,
  code                          varchar(255) not null,
  time_updated                  timestamp not null,
  time_created                  timestamp not null,
  constraint pk_vote primary key (voter_id,poll_id)
);

create table voter (
  id                            bigserial not null,
  status                        varchar(255) not null,
  code                          varchar(255) not null,
  role                          varchar(255) not null,
  email                         varchar(255) not null,
  username                      varchar(20) not null,
  password_hash                 varchar(255),
  access_token                  varchar(255),
  time_updated                  timestamp not null,
  time_created                  timestamp not null,
  constraint pk_voter primary key (id)
);

alter table option add constraint fk_option_poll_id foreign key (poll_id) references poll (id) on delete restrict on update restrict;
create index ix_option_poll_id on option (poll_id);

alter table participation add constraint fk_participation_voter_id foreign key (voter_id) references voter (id) on delete restrict on update restrict;
create index ix_participation_voter_id on participation (voter_id);

alter table participation add constraint fk_participation_poll_id foreign key (poll_id) references poll (id) on delete restrict on update restrict;
create index ix_participation_poll_id on participation (poll_id);

alter table poll add constraint fk_poll_creator_id foreign key (creator_id) references voter (id) on delete restrict on update restrict;
create index ix_poll_creator_id on poll (creator_id);

alter table vote add constraint fk_vote_voter_id foreign key (voter_id) references voter (id) on delete restrict on update restrict;
create index ix_vote_voter_id on vote (voter_id);

alter table vote add constraint fk_vote_poll_id foreign key (poll_id) references poll (id) on delete restrict on update restrict;
create index ix_vote_poll_id on vote (poll_id);


# --- !Downs

alter table if exists option drop constraint if exists fk_option_poll_id;
drop index if exists ix_option_poll_id;

alter table if exists participation drop constraint if exists fk_participation_voter_id;
drop index if exists ix_participation_voter_id;

alter table if exists participation drop constraint if exists fk_participation_poll_id;
drop index if exists ix_participation_poll_id;

alter table if exists poll drop constraint if exists fk_poll_creator_id;
drop index if exists ix_poll_creator_id;

alter table if exists vote drop constraint if exists fk_vote_voter_id;
drop index if exists ix_vote_voter_id;

alter table if exists vote drop constraint if exists fk_vote_poll_id;
drop index if exists ix_vote_poll_id;

drop table if exists option cascade;

drop table if exists participation cascade;

drop table if exists poll cascade;

drop table if exists vote cascade;

drop table if exists voter cascade;

