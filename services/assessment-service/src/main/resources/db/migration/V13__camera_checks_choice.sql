-- =====================================================================
-- Which checks a camera test runs. The trainer picks them when making the test:
--   FACE      a face in view and inside the oval
--   PEOPLE    only the student in view (a second face or body is reported)
--   IDENTITY  the face stays the one recorded at the start
--   PHONE     a mobile phone in view is reported
-- The camera being on is always required. Tests made before this choice existed keep running all four.
-- =====================================================================
ALTER TABLE quizzes
    ADD COLUMN camera_checks VARCHAR(60) NOT NULL DEFAULT 'FACE,IDENTITY,PEOPLE,PHONE';
