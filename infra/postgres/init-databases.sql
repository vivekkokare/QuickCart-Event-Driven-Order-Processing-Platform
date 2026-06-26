-- Runs only on first container initialization (empty data volume).
-- order_system is created automatically via POSTGRES_DB; this script adds
-- the additional per-service databases so each microservice owns its own
-- schema and cannot reach into another service's tables directly.
CREATE DATABASE inventory_system OWNER order_admin;
