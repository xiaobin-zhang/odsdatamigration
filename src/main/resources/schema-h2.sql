drop table if exists validation_task;
drop table if exists validation_batch;

create table validation_batch (
  batch_id varchar(64) primary key,
  status varchar(32),
  total_count int,
  pending_count int,
  running_count int,
  pass_count int,
  fail_count int,
  error_count int,
  skipped_count int,
  current_pair_name varchar(128),
  current_source_table varchar(128),
  current_target_table varchar(128),
  current_check_type varchar(64),
  start_time timestamp,
  end_time timestamp,
  update_time timestamp
);

create table validation_task (
  id bigint auto_increment primary key,
  batch_id varchar(64),
  pair_name varchar(128),
  source_name varchar(128),
  target_name varchar(128),
  source_table varchar(128),
  target_table varchar(128),
  check_type varchar(64),
  shard_no int,
  status varchar(32),
  retry_count int,
  source_sql clob,
  target_sql clob,
  error_message clob,
  result_summary clob
);
