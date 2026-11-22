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

INSERT INTO fee_item (code, name, coverage_type, executable_before_payment)
VALUES ('EX-RAT', '신속항원검사(예시)', 'COVERED', 1);

INSERT INTO fee_price (fee_item_id, effective_from, amount)
VALUES ((SELECT id FROM fee_item WHERE code = 'EX-RAT'), DATE '2026-01-01', 16000);

INSERT INTO fee_item (code, name, coverage_type, executable_before_payment)
VALUES ('EX-CBC', '일반혈액검사(예시)', 'COVERED', 0);

INSERT INTO fee_price (fee_item_id, effective_from, amount)
VALUES ((SELECT id FROM fee_item WHERE code = 'EX-CBC'), DATE '2026-01-01', 5000);

INSERT INTO fee_item (code, name, coverage_type, executable_before_payment)
VALUES ('EX-CT-HEAD', '두부 CT(예시)', 'COVERED', 0);

INSERT INTO fee_price (fee_item_id, effective_from, amount)
VALUES ((SELECT id FROM fee_item WHERE code = 'EX-CT-HEAD'), DATE '2026-01-01', 120000);

INSERT INTO fee_price (fee_item_id, effective_from, amount)
VALUES ((SELECT id FROM fee_item WHERE code = 'EX-CT-HEAD'), DATE '2026-07-01', 125000);

INSERT INTO fee_item (code, name, coverage_type, executable_before_payment)
VALUES ('EX-NC-CERT', '진단서 발급(예시)', 'NON_COVERED', 0);

INSERT INTO fee_price (fee_item_id, effective_from, amount)
VALUES ((SELECT id FROM fee_item WHERE code = 'EX-NC-CERT'), DATE '2026-01-01', 20000);

INSERT INTO counter (code, name)
VALUES ('C01', '원무 1번 창구');
