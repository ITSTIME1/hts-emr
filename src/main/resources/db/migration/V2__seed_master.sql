-- dummy data insert (실제 수가가 아니고 예시값)
INSERT INTO fee_item (code, name, coverage_type, executable_before_payment)
VALUES ('EX-CONSULT', '진찰료(예시)', 'COVERED', 1);

INSERT INTO fee_price (fee_item_id, effective_from, amount)
VALUES (
        (
            SELECT
                id
            FROM fee_item
            WHERE code = 'EX-CONSULT'
        ),
        DATE '2026-01-01',
        18000
       );

-- 선시행 항목 추가
INSERT INTO fee_item (code, name, coverage_type, executable_before_payment)
VALUES ('EX-RAT', '신속항원검사(예시)', 'COVERED', 1);

INSERT INTO fee_price (fee_item_id, effective_from, amount)
VALUES ((SELECT id FROM fee_item WHERE code = 'EX-RAT'), DATE '2026-01-01', 16000);

-- 혈액검사 (급여, 수납 후 시행)
INSERT INTO fee_item (code, name, coverage_type, executable_before_payment)
VALUES ('EX-CBC', '일반혈액검사(예시)', 'COVERED', 0);

INSERT INTO fee_price (fee_item_id, effective_from, amount)
VALUES ((SELECT id FROM fee_item WHERE code = 'EX-CBC'), DATE '2026-01-01', 5000);

-- CT (급여, 단가 두 개: 진료일에 따라 다른 단가가 적용되는지 확인용)
INSERT INTO fee_item (code, name, coverage_type, executable_before_payment)
VALUES ('EX-CT-HEAD', '두부 CT(예시)', 'COVERED', 0);

INSERT INTO fee_price (fee_item_id, effective_from, amount)
VALUES ((SELECT id FROM fee_item WHERE code = 'EX-CT-HEAD'), DATE '2026-01-01', 120000);

INSERT INTO fee_price (fee_item_id, effective_from, amount)
VALUES ((SELECT id FROM fee_item WHERE code = 'EX-CT-HEAD'), DATE '2026-07-01', 125000);

-- 비급여 항목 (자격과 관계없이 전액 본인부담)
INSERT INTO fee_item (code, name, coverage_type, executable_before_payment)
VALUES ('EX-NC-CERT', '진단서 발급(예시)', 'NON_COVERED', 0);

INSERT INTO fee_price (fee_item_id, effective_from, amount)
VALUES ((SELECT id FROM fee_item WHERE code = 'EX-NC-CERT'), DATE '2026-01-01', 20000);

-- 창구
INSERT INTO counter (code, name)
VALUES ('C01', '원무 1번 창구');

