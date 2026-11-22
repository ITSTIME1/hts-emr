drop index idx_payment_invoice;

alter table payment add constraint uk_payment_invoice unique (invoice_id);

comment on column payment.invoice_id is '바탕 산정 id(billing). 모듈이 달라 외래 키를 걸지 않는다. 한 산정에 수납 건 하나(INV-05, uk_payment_invoice)';
