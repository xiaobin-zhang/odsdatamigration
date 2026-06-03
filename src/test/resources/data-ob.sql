insert into t_order(order_id,user_id,mobile,order_amount,pay_amount,pay_time,create_time,status,remark) values
(1,10,'13800000001',100.10,100.10,timestamp '2026-01-01 10:00:00',timestamp '2026-01-01 09:00:00','PAID','正常'),
(2,20,null,200.20,199.20,null,timestamp '2026-01-02 09:00:00','NEW','待支付'),
(3,30,'13800000003',300.30,300.30,timestamp '2026-01-02 11:00:00',timestamp '2026-01-02 10:00:00','PAID','备注');

insert into t_user(user_id,user_name,mobile,email,create_time) values
(1,'张三','13800000001','a@example.com',timestamp '2026-01-01 00:00:00'),
(2,'李四',null,'b@example.com',timestamp '2026-01-02 00:00:00');

insert into t_null_only(id,name,remark) values
(1,'a',null),
(2,null,'b');
