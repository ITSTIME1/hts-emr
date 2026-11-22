alter table payment_item add completed number(1) default 0 not null;

alter table payment_item add constraint ck_payment_item_completed check (completed in (0, 1));

create unique index uk_payment_item_completed on payment_item (case when completed = 1 then order_item_id end);

comment on column payment_item.completed is '완료된 수납 건의 항목이면 1. 완료된 항목끼리만 유일하다(INV-03, uk_payment_item_completed)';
