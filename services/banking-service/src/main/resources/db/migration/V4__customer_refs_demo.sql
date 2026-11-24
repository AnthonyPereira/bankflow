-- Client de démonstration : un administrateur de démonstration n'agit que sur ces clients
ALTER TABLE customer_refs ADD COLUMN demo BOOLEAN NOT NULL DEFAULT FALSE;
