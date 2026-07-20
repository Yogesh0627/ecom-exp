-- V12: self-service profile + secure email change.
--
-- Email change is verify-BEFORE-switch: the user's current email stays active until they click a
-- link sent to the NEW address. We stage the target in users.pending_email and record it on the
-- verification token; consuming that token swaps email and clears the pending value.

ALTER TABLE users ADD COLUMN pending_email TEXT;

-- When set, verifying this token switches the user's email to new_email (an email-CHANGE token);
-- when null, the token just marks the current email verified (a first-time verification).
ALTER TABLE email_verification_tokens ADD COLUMN new_email TEXT;
