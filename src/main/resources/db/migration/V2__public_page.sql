-- Serviços marcados como públicos aparecem na página de status aberta (sem login).
ALTER TABLE monitored_services ADD COLUMN public_page BOOLEAN DEFAULT FALSE NOT NULL;
