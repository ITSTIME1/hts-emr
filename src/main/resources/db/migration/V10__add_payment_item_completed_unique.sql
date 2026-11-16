-- 오더 항목은 완료된 수납 건 하나에만 포함된다(INV-03). V7에서 비워 둔 제약을 건다(C-03, ADR-0003).
--
-- [왜 order_item_id 단독 유일 제약이 아닌가]
-- 같은 항목이 여러 수납 건에 들어가는 것은 정상이다. 진행 중인 항목도 다시 산정할 수 있고,
-- 거부·보류된 수납 건의 항목은 새 수납 건에 다시 들어간다. 막아야 하는 것은 "완료된 수납 건 둘"뿐이다.
-- 그래서 완료된 수납 건의 항목에만 표시(completed = 1)를 남기고, 표시된 행끼리만 유일하게 한다.
--
-- [함수 기반 유일 인덱스]
-- case 식은 완료된 항목이면 order_item_id, 아니면 null이다. Oracle은 키가 모두 null인 행을 인덱스에 넣지 않으므로,
-- 완료되지 않은 항목은 몇 개가 겹쳐도 상관없고 완료된 항목끼리만 겹치면 ORA-00001이 난다.
--
-- [왜 잠금이 아닌가]
-- 두 수납 건이 모두 이번 호출에서 새로 생겨 한 번에 다 내면, 서로의 행이 아직 커밋되지 않아 보이지 않는다.
-- 이미 있는 행을 잠그는 방식은 이 경우를 막지 못한다(PaymentConcurrencyTest로 확인, ADR-0003).
-- 유일 인덱스는 커밋되지 않은 키도 보므로, 뒤쪽은 앞쪽이 끝날 때까지 기다렸다가 실패한다.
--
-- 완료된 수납 건이 나중에 환불로 취소되면 그 항목의 표시를 지워야 한다(환불 슬라이스에서 만든다).

alter table payment_item add completed number(1) default 0 not null;

alter table payment_item add constraint ck_payment_item_completed check (completed in (0, 1));

create unique index uk_payment_item_completed on payment_item (case when completed = 1 then order_item_id end);

comment on column payment_item.completed is '완료된 수납 건의 항목이면 1. 완료된 항목끼리만 유일하다(INV-03, uk_payment_item_completed)';
