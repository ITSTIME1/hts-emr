alter table payment_line rename column applied_amount to amount;

alter table payment_line rename constraint ck_payment_line_applied to ck_payment_line_amount;

comment on column payment_line.amount is '결제 금액. 결제 합계에는 이 값만 산입한다(T-PLN-12, A-20)';
comment on column payment_line.received_amount is '환자에게서 받은 금액. 거스름돈 = 받은 금액 − 결제 금액';
