drop table if exists t_order;
drop table if exists t_user;
drop table if exists t_null_only;
drop table if exists t_composite;
drop table if exists t_log;
drop table if exists t_skip;

create table t_order (
  order_id bigint primary key,
  user_id bigint,
  mobile varchar(32),
  order_amount decimal(18,2),
  pay_amount decimal(18,2),
  pay_time timestamp,
  create_time timestamp,
  status varchar(20),
  remark varchar(200)
);

create table t_user (
  user_id bigint primary key,
  user_name varchar(64),
  mobile varchar(32),
  email varchar(128),
  create_time timestamp
);

create table t_null_only (
  id bigint primary key,
  name varchar(64),
  remark varchar(128)
);

create table t_composite (
  tenant_id bigint,
  order_id bigint,
  status varchar(20),
  remark varchar(128),
  primary key (tenant_id, order_id)
);

create table t_log (id bigint primary key, content varchar(64));
create table t_skip (id bigint primary key, content varchar(64));
