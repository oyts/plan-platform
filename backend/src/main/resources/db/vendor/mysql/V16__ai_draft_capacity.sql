-- Draft validation accepts up to 128000 characters, which can exceed TEXT's 64 KiB.
-- Only widen the AI draft column; no business records are rewritten or deleted.
ALTER TABLE ai_sessions MODIFY COLUMN draft_json MEDIUMTEXT;
