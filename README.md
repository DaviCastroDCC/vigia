# Vigia

API REST que monitora sites e APIs: verifica cada URL periodicamente, salva o status e o tempo de resposta e calcula o uptime.

**Stack:** Java 21 · Spring Boot 3.5 · Spring Security + JWT · Spring Data JPA · PostgreSQL · Flyway · Docker

## Como funciona

1. O usuário se cadastra e recebe um token JWT.
2. Cadastra os serviços que quer monitorar (nome, URL, intervalo em segundos).
3. Uma tarefa `@Scheduled` roda a cada 10 s, pega os serviços cujo intervalo já venceu e verifica todos **em paralelo com virtual threads**, para que um site lento não atrase os outros.
4. Cada verificação é salva em `health_checks` (status HTTP, tempo de resposta, erro). Status menor que 400 conta como **UP**.
5. Os endpoints de histórico e de uptime leem essas verificações.

```
src/main/java/com/vigia
├── auth/       cadastro, login, entidade User
├── security/   JwtService, filtro JWT, SecurityConfig
├── service/    CRUD dos serviços monitorados + histórico e uptime
├── check/      agendador, cliente HTTP, gravação dos resultados, proteção contra SSRF
├── common/     tratamento de erros (RFC 7807)
└── config/     propriedades da aplicação
```

## Rodando

### Com Docker (API + PostgreSQL)

```bash
cp .env.example .env      # e troque os valores
docker compose up -d --build
```

### Sem Docker (precisa de um PostgreSQL local)

```bash
# banco "vigia", usuário "vigia", senha "vigia" (ou defina DB_URL, DB_USER, DB_PASSWORD)
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
| GET | `/api/public/status` | Mesmo formato, só dos serviços marcados como públicos (sem login) |
| GET | `/actuator/health` | Health check da própria API |

## Página de status

Acesse `http://localhost:8080/`. Sem login, ela mostra os serviços marcados como **públicos**: status em verde ou vermelho, uptime de 24h, tempo médio de resposta, uma barra com as últimas 60 verificações e um gráfico do tempo de resposta. Em **Meu painel**, depois do login, você adiciona, verifica, exclui e escolhe quais serviços aparecem na página pública. A página se atualiza sozinha a cada 15 s.

Todas as rotas `/api/services/**` exigem `Authorization: Bearer <token>`. Cada usuário só enxerga os próprios serviços.

### Exemplo

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"name":"Davi","email":"davi@exemplo.com","password":"senha-segura"}' | jq -r .token)

curl -s -X POST localhost:8080/api/services \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"GitHub","url":"https://github.com","intervalSeconds":60}'

curl -s localhost:8080/api/services/1/uptime?hours=24 -H "Authorization: Bearer $TOKEN"
# {"serviceId":1,"periodHours":24,"totalChecks":1440,"upChecks":1438,"uptimePercent":99.86,"avgResponseTimeMs":212.4}
```

## Configuração

| Variável | Padrão | Uso |
|---|---|---|
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | `localhost:5432/vigia` | Conexão com o PostgreSQL |
| `JWT_SECRET` | segredo de desenvolvimento | **Obrigatório em produção**, mínimo de 32 caracteres |
| `JWT_EXPIRATION_MINUTES` | `120` | Validade do token |
| `CHECKER_TICK_MS` | `10000` | De quanto em quanto tempo o agendador procura verificações pendentes |
| `CHECKER_TIMEOUT_SECONDS` | `10` | Tempo máximo de cada requisição |
| `CHECKER_BLOCK_PRIVATE` | `true` | Bloqueia URLs internas (localhost, 10.x, 192.168.x, 169.254.169.254) |

### Por que bloquear URLs internas?

Como qualquer usuário cadastra uma URL e o servidor faz a requisição, sem esse bloqueio alguém poderia usar a API para acessar a rede interna da AWS, por exemplo o endpoint de metadados `169.254.169.254`, que expõe credenciais da instância. Isso se chama SSRF. Por isso a API também não segue redirecionamentos: um 3xx já conta como "no ar".

## Deploy na AWS (EC2 + Docker Compose)

1. Crie uma instância EC2 (t3.micro ou t4g.small, Amazon Linux 2023) e libere a porta 8080 (ou 80/443 se usar um proxy) no Security Group.
2. Instale Docker e o plugin Compose:
   ```bash
   sudo dnf install -y docker git && sudo systemctl enable --now docker
   sudo mkdir -p /usr/local/lib/docker/cli-plugins
   sudo curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-$(uname -m) \
     -o /usr/local/lib/docker/cli-plugins/docker-compose && sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
   ```
3. Clone o repositório, crie o `.env` com senhas fortes e rode `sudo docker compose up -d --build`.

Próximo passo mais robusto: banco no RDS e a API no ECS/Fargate ou App Runner.

## Próximas versões

- [ ] Alerta por e-mail ou Telegram quando um serviço muda de UP para DOWN
- [x] Página pública de status com gráfico de tempo de resposta (`/`)
- [x] Documentação OpenAPI/Swagger (`/swagger-ui.html`)
- [ ] CI no GitHub Actions rodando `mvn test`
- [ ] Limpeza automática de verificações antigas
