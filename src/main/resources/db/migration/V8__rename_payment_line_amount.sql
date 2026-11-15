-- 결제의 applied_amount를 amount로 바꾼다. "수납 반영 금액"이라는 이름으로는 무엇이 반영됐는지 알 수 없어서,
-- 도메인 문서(01-flow F-04)가 처음부터 부르던 "금액(결제 금액)"으로 맞춘다.
-- V7은 이미 main에 머지되어 고치지 않는다. 이미 V7을 실행한 DB에서도 이 파일만 이어서 실행된다.
--
-- 체크 제약의 식(received_amount >= applied_amount)은 Oracle이 컬럼 이름 변경을 따라 자동으로 고친다.
-- 이름에 옛 컬럼명이 들어간 제약만 따로 바꾼다.

alter table payment_line rename column applied_amount to amount;

alter table payment_line rename constraint ck_payment_line_applied to ck_payment_line_amount;

comment on column payment_line.amount is '결제 금액. 결제 합계에는 이 값만 산입한다(T-PLN-12, A-20)';
comment on column payment_line.received_amount is '환자에게서 받은 금액. 거스름돈 = 받은 금액 − 결제 금액';
