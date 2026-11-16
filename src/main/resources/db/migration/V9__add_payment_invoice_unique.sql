-- 한 산정을 바탕으로 한 수납 건은 하나뿐이다(INV-05). V7에서 비워 둔 유일 제약을 건다(C-06, ADR-0003).
--
-- [왜 유일 제약인가]
-- 서비스는 수납 건을 만들기 전에 그 산정의 수납 건이 있는지 확인한다(existsByInvoiceId).
-- 하지만 두 창구가 같은 순간에 첫 결제를 받으면, 둘 다 "없음"을 본 뒤 둘 다 만든다.
-- 이때는 아직 행이 없어서 잠글 행도(select ... for update), 비교할 버전도(@Version) 없다.
-- 없는 것이 둘 생기는 것은 DB 제약만 막을 수 있다.
--
-- [막는 방식]
-- 뒤에 insert한 쪽은 앞의 트랜잭션이 끝날 때까지 기다린다.
-- 앞쪽이 커밋하면 뒤쪽은 ORA-00001로 실패하고 롤백된다. 뒤쪽 창구의 결제는 남지 않으므로 다시 시도해야 하고,
-- 다시 시도하면 앞쪽이 만든 진행 중인 수납 건에 이어서 받는다.
--
-- 같은 컬럼의 조회용 인덱스(V7)를 지우고, 유일 제약이 만드는 유일 인덱스가 조회도 맡는다.

drop index idx_payment_invoice;

alter table payment add constraint uk_payment_invoice unique (invoice_id);

comment on column payment.invoice_id is '바탕 산정 id(billing). 모듈이 달라 외래 키를 걸지 않는다. 한 산정에 수납 건 하나(INV-05, uk_payment_invoice)';
