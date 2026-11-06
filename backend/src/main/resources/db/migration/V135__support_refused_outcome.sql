ALTER TABLE penny_support_calls DROP CONSTRAINT penny_support_calls_outcome_check;
ALTER TABLE penny_support_calls ADD CONSTRAINT penny_support_calls_outcome_check CHECK(outcome IN ('IN_PROGRESS','RESOLVED','PARTIAL','UNRESOLVED','REFUSED'));
