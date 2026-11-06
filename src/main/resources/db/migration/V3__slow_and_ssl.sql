-- Acima deste tempo de resposta o serviço aparece como "lento" (DEGRADED).
ALTER TABLE monitored_services ADD COLUMN slow_threshold_ms INTEGER DEFAULT 1000 NOT NULL;
-- Data de validade do certificado TLS visto na última verificação HTTPS.
ALTER TABLE monitored_services ADD COLUMN cert_expires_at TIMESTAMP WITH TIME ZONE;
