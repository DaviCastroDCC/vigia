# Vigia

![uptime](https://vigia-status.duckdns.org/api/public/services/7/badge.svg)

API REST que monitora sites e APIs: verifica cada URL periodicamente, salva o status e o tempo de resposta e calcula o uptime.

**Demonstração ao vivo:** https://vigia-status.duckdns.org (o badge acima é gerado pelo próprio Vigia, monitorando a si mesmo)

**Stack:** Java 21 · Spring Boot 3.5 · Spring Security + JWT · Spring Data JPA · PostgreSQL · Flyway · Docker

## Como funciona

1. O usuário se cadastra e recebe um token JWT.
2. Cadastra os serviços que quer monitorar (nome, URL, intervalo em segundos).
3. Uma tarefa `@Scheduled` roda a cada 10 s, pega os serviços cujo intervalo já venceu e verifica todos **em paralelo com virtual threads**, para que um site lento não atrase os outros.
4. Cada verificação é salva em `health_checks` (status HTTP, tempo de resposta, erro). Status menor que 400 conta como **UP**; se a resposta passar do limite configurado no serviço (padrão 1000 ms), ele fica **DEGRADED** (lento), mas continua contando no uptime.
5. Em URLs HTTPS, a validade do certificado é lida da própria conexão TLS, sem requisição extra.
6. **Incidentes:** depois de 2 falhas seguidas (uma falha isolada pode ser só oscilação de rede), um incidente é aberto com a hora da primeira falha e a causa. Ele é encerrado na primeira verificação bem-sucedida. Tudo acontece na mesma transação que salva a verificação.
7. **Alertas no Telegram:** quando um incidente abre ou fecha, o dono do serviço recebe uma mensagem. O aviso só sai depois do commit, para nunca alertar sobre algo que não ficou salvo.
8. Os endpoints de histórico, uptime, resumo diário, incidentes e badge leem esses dados.

```
src/main/java/com/vigia
├── auth/       cadastro, login, entidade User
├── account/    dados da conta e conexão com o Telegram
├── security/   JwtService, filtro JWT, SecurityConfig
├── service/    CRUD dos serviços monitorados, histórico, uptime, resumo diário e badge
├── check/      agendador, cliente HTTP, gravação dos resultados, proteção contra SSRF
├── incident/   abertura/encerramento de incidentes e envio dos alertas
├── telegram/   cliente da Bot API e vínculo do chat com a conta
├── common/     tratamento de erros (RFC 7807)
└── config/     propriedades da aplicação
```

### Conectando o Telegram sem copiar ID

No painel, **Conectar Telegram** gera um link `t.me/<bot>?start=<código>` de uso único. Ao tocar em **Iniciar**, o Telegram envia `/start <código>` ao bot; o Vigia lê essa mensagem (polling com `getUpdates`, sem precisar de webhook público), encontra a conta pelo código e passa a mandar os alertas para aquele chat.

## Rodando

### Com Docker (API + PostgreSQL)

```bash
cp .env.example .env      # e troque os valores
docker compose up -d --build
```

### Sem Docker (precisa de um PostgreSQL local)

```bash
# configure a conexão com o banco nas variáveis listadas no .env.example
mvn spring-boot:run
```

### Testes

```bash
mvn test
```

Os testes usam H2 em memória e um servidor HTTP falso, sem precisar de rede nem de Docker.

## Endpoints

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/auth/register` | Cria a conta e devolve o token |
| POST | `/api/auth/login` | Devolve o token |
| GET | `/api/services` | Lista seus serviços com o último status |
| POST | `/api/services` | Cadastra um serviço |
| GET | `/api/services/{id}` | Detalhe |
| PUT | `/api/services/{id}` | Atualiza |
| DELETE | `/api/services/{id}` | Remove (junto com o histórico) |
| POST | `/api/services/{id}/check` | Verifica agora, sem esperar o agendador |
| GET | `/api/services/{id}/checks?page=0&size=50` | Histórico, do mais recente ao mais antigo |
| GET | `/api/services/{id}/uptime?hours=24` | Uptime em % e tempo médio de resposta |
| GET | `/api/services/dashboard` | Seus serviços com uptime 24h e últimas 60 verificações |
| GET | `/api/public/status` | Mesmo formato, só dos serviços marcados como públicos (sem login), com os incidentes recentes |
| GET | `/api/public/services/{id}/badge.svg?days=30` | Badge SVG de uptime para README (só serviços públicos) |
| GET | `/api/me` | Dados da conta e se o Telegram está conectado |
| POST | `/api/me/telegram` | Gera o link de uso único para conectar o Telegram |
| DELETE | `/api/me/telegram` | Desconecta o Telegram |
| GET | `/actuator/health` | Health check da própria API |

## Página de status

Acesse `http://localhost:8080/`. Sem login, ela mostra os serviços marcados como **públicos**:

- status **online** (verde), **lento** (amarelo) ou **fora do ar** (vermelho);
- uptime de 24 h, tempo médio de resposta e dias até o certificado SSL vencer (em destaque quando faltam menos de 14);
- as últimas 60 verificações e um gráfico do tempo de resposta;
- uma barra com os **últimos 90 dias**, um traço por dia, como nas páginas de status de grandes serviços. O resumo diário é agregado no banco e guardado em memória por alguns minutos, para não reprocessar 90 dias de dados a cada atualização da página.

Em **Meu painel**, depois do login, você adiciona, verifica, exclui, escolhe quais serviços aparecem na página pública e copia o Markdown do badge de uptime. A página se atualiza sozinha a cada 15 s.

Todas as rotas `/api/services/**` exigem `Authorization: Bearer <token>`. Cada usuário só enxerga os próprios serviços.

### Exemplo

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"name":"Usuario","email":"usuario@exemplo.com","password":"sua-senha"}' | jq -r .token)

curl -s -X POST localhost:8080/api/services \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"GitHub","url":"https://github.com","intervalSeconds":60}'

curl -s localhost:8080/api/services/1/uptime?hours=24 -H "Authorization: Bearer $TOKEN"
# {"serviceId":1,"periodHours":24,"totalChecks":1440,"upChecks":1438,"uptimePercent":99.86,"avgResponseTimeMs":212.4}
```

## Segurança

As credenciais (banco e segredo do JWT) ficam em variáveis de ambiente; o `.env.example` mostra quais são. O `.env` real nunca vai para o repositório.

### Por que bloquear URLs internas?

Como qualquer usuário cadastra uma URL e o servidor faz a requisição, sem esse bloqueio alguém poderia usar a API para acessar a rede interna da AWS, por exemplo o endpoint de metadados `169.254.169.254`, que expõe credenciais da instância. Isso se chama SSRF. Por isso a API também não segue redirecionamentos: um 3xx já conta como "no ar".

## Deploy

Roda na AWS em uma instância EC2 com Docker Compose. Em produção, o [Caddy](https://caddyserver.com) fica na frente da API e cuida do HTTPS (certificado Let's Encrypt renovado automaticamente); a porta 8080 não fica exposta.

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

## Próximas versões

- [ ] Alertas também por e-mail
- [ ] CI no GitHub Actions rodando `mvn test`
- [ ] Limpeza automática de verificações antigas
