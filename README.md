
# CommerceCore API

[![CommerceCore CI](https://github.com/calazans-999/commercecore-api/actions/workflows/ci.yml/badge.svg)](https://github.com/calazans-999/commercecore-api/actions/workflows/ci.yml)

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.2-green)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue)
![Docker](https://img.shields.io/badge/Docker-Compose-blue)
![Tests](https://img.shields.io/badge/Tests-63_passed-brightgreen)

Backend de e-commerce desenvolvido com Java 21, Spring Boot 4.1.2, PostgreSQL e Docker, com autenticação JWT, controle de estoque, pagamentos simulados e testes automatizados.

## Sobre o projeto

O CommerceCore é uma API REST desenvolvida para simular as principais operações de uma plataforma de comércio eletrônico.

O projeto foi construído com foco em arquitetura backend, segurança, regras de negócio, integridade de dados, concorrência e integração contínua.

O sistema permite:

- Cadastrar e autenticar usuários.
- Gerenciar produtos e categorias.
- Adicionar e remover produtos do carrinho.
- Finalizar compras com controle transacional de estoque.
- Consultar o histórico de pedidos.
- Cancelar pedidos elegíveis com reposição de estoque.
- Criar e consultar pagamentos simulados.
- Simular aprovação e rejeição de pagamentos em ambiente controlado.
- Prevenir operações duplicadas e inconsistências decorrentes de concorrência.

Nenhuma transação financeira real é realizada.

## Tecnologias utilizadas

- Java 21
- Spring Boot 4.1.2
- Spring Web MVC
- Spring Security
- JWT com assinatura HS256
- BCrypt
- Spring Data JPA
- Hibernate ORM
- PostgreSQL 16
- Flyway
- Maven
- Docker
- Docker Compose
- Swagger / OpenAPI
- Spring Boot Actuator
- JUnit 5
- Mockito
- MockMvc
- GitHub Actions

## Arquitetura

A aplicação utiliza uma arquitetura em camadas:

```text
Cliente HTTP
     |
     v
Controller
     |
     v
Service
     |
     v
Repository
     |
     v
PostgreSQL
```

### Organização dos pacotes

| Pacote | Responsabilidade |
|---|---|
| `controller` | Endpoints REST |
| `service` | Regras de negócio e transações |
| `repository` | Persistência com Spring Data JPA |
| `entity` | Entidades e relacionamentos JPA |
| `dto` | Contratos de entrada e saída da API |
| `config` | Configurações do Spring e segurança |
| `security` | Componentes relacionados à autenticação e autorização |

### Princípios utilizados

- Separação de responsabilidades.
- Injeção de dependências.
- API REST.
- Autenticação stateless com JWT.
- Persistência relacional.
- Migrações versionadas.
- Transações para operações críticas.
- Bloqueio pessimista em operações concorrentes.
- Validação de regras de negócio.
- Testes automatizados.

## Funcionalidades

### Autenticação e autorização

- Cadastro de usuários.
- Login com JWT.
- Armazenamento de senhas com BCrypt.
- Perfis `CUSTOMER` e `ADMIN`.
- Proteção de endpoints.
- Validação do usuário autenticado.
- Verificação de contas habilitadas.
- Controle de acesso para operações administrativas.

### Produtos e categorias

- Cadastro de produtos e categorias.
- Listagem e consulta.
- Atualização de informações.
- Desativação de produtos.
- Paginação e filtros.
- Controle de estoque.
- Operações administrativas restritas ao perfil `ADMIN`.

### Carrinho de compras

- Consultar carrinho.
- Adicionar produtos.
- Atualizar quantidades.
- Remover itens.
- Esvaziar carrinho.
- Calcular o valor total.
- Manter os dados separados por usuário.

### Pedidos e checkout

- Finalizar compras.
- Verificar disponibilidade de estoque.
- Descontar produtos do estoque.
- Registrar preços e nomes dos produtos no momento da compra.
- Calcular o valor total do pedido.
- Esvaziar o carrinho após checkout.
- Consultar pedidos do usuário autenticado.
- Impedir acesso aos pedidos de outros clientes.
- Executar checkout dentro de uma transação.
- Utilizar bloqueios pessimistas para operações concorrentes.

### Cancelamento de pedidos

- Cancelamento pelo proprietário do pedido.
- Reposição automática do estoque.
- Proteção contra cancelamento duplicado.
- Retorno HTTP 409 para pedidos que não podem ser cancelados.
- Bloqueios pessimistas para manter consistência.
- Operações transacionais para evitar atualizações parciais.

O cancelamento também considera o estado do pagamento:

| Pagamento | Comportamento |
|---|---|
| Sem pagamento | Cancelamento permitido |
| `PENDING` | Pagamento rejeitado e pedido cancelado |
| `REJECTED` | Cancelamento permitido |
| `APPROVED` | Cancelamento bloqueado até existir fluxo de estorno |

Atualmente, não existe processamento de estornos financeiros.

### Pagamentos simulados

O CommerceCore possui um módulo de pagamentos fictícios, destinado exclusivamente a demonstração e testes.

Funcionalidades:

- Criação de pagamentos vinculados a pedidos.
- Consulta de pagamentos.
- Estados `PENDING`, `APPROVED` e `REJECTED`.
- Apenas um registro de pagamento por pedido.
- Validação de propriedade do pedido.
- Proteção contra criação duplicada.
- Impedimento de processamento duplicado.
- Integração com cancelamento de pedidos.
- Bloqueios pessimistas no PostgreSQL.
- Testes de integração e concorrência.

#### Fluxo do pagamento

```text
Pedido PLACED
      |
      v
Pagamento PENDING
      |
      +---------------------+
      |                     |
      v                     v
  APPROVED               REJECTED
```

Um pagamento já processado não pode ser aprovado ou rejeitado novamente.

#### Segurança da simulação

As operações demonstrativas de aprovação e rejeição são controladas pela configuração:

`COMMERCECORE_PAYMENT_SIMULATION_ENABLED`

O valor padrão é `false`.

Com a simulação desabilitada:

- `POST /payment/approve` retorna HTTP 403 para usuários autenticados.
- `POST /payment/reject` retorna HTTP 403 para usuários autenticados.
- Usuários sem autenticação recebem HTTP 401.
- Criação e consulta de pagamentos continuam disponíveis para o proprietário do pedido.

Nos testes automatizados, a simulação é habilitada explicitamente.

**Importante:** habilitar a simulação permite que o próprio cliente autenticado determine o resultado de seu pagamento. Portanto, essa funcionalidade é destinada apenas a ambientes de desenvolvimento e demonstração controlados.

Em um e-commerce real, a confirmação de pagamentos deve ser realizada por um provedor confiável, com validação de notificações, autenticação da integração e mecanismos de prevenção contra fraudes.

## Endpoints principais

### Autenticação

| Método | Endpoint | Descrição |
|---|---|---|
| POST | `/api/v1/auth/register` | Registrar usuário |
| POST | `/api/v1/auth/login` | Realizar login |
| GET | `/api/v1/auth/me` | Consultar perfil autenticado |

### Produtos e categorias

| Método | Endpoint | Descrição |
|---|---|---|
| GET | `/api/v1/products` | Listar produtos |
| GET | `/api/v1/categories` | Listar categorias |
| POST | `/api/v1/products` | Cadastrar produto |
| POST | `/api/v1/categories` | Cadastrar categoria |

Operações de criação, atualização e exclusão administrativa exigem as permissões correspondentes.

### Carrinho

| Método | Endpoint | Descrição |
|---|---|---|
| GET | `/api/v1/cart` | Consultar carrinho |
| POST | `/api/v1/cart/items` | Adicionar produto |
| PUT | `/api/v1/cart/items/{itemId}` | Alterar quantidade |
| DELETE | `/api/v1/cart/items/{itemId}` | Remover produto |
| DELETE | `/api/v1/cart` | Esvaziar carrinho |

### Pedidos

| Método | Endpoint | Descrição |
|---|---|---|
| POST | `/api/v1/orders/checkout` | Finalizar compra |
| GET | `/api/v1/orders` | Listar pedidos |
| GET | `/api/v1/orders/{id}` | Consultar pedido |
| POST | `/api/v1/orders/{id}/cancel` | Cancelar pedido elegível e repor estoque |

### Pagamentos

| Método | Endpoint | Descrição |
|---|---|---|
| POST | `/api/v1/orders/{orderId}/payment` | Criar pagamento pendente |
| GET | `/api/v1/orders/{orderId}/payment` | Consultar pagamento |
| POST | `/api/v1/orders/{orderId}/payment/approve` | Simular aprovação |
| POST | `/api/v1/orders/{orderId}/payment/reject` | Simular rejeição |

Os dois últimos endpoints ficam bloqueados por padrão.

## Executando com Docker

### Requisitos

- Docker Desktop
- Docker Compose

### Configuração

Copie o arquivo `.env.example` para `.env`:

```powershell
Copy-Item .env.example .env
```

Configure:

- `POSTGRES_PASSWORD`
- `COMMERCECORE_JWT_SECRET`

Exemplo da estrutura do arquivo `.env`:

```dotenv
POSTGRES_PASSWORD=substitua_por_uma_senha_local_forte
COMMERCECORE_JWT_SECRET=substitua_por_uma_chave_base64_valida
COMMERCECORE_PAYMENT_SIMULATION_ENABLED=false
```

O arquivo `.env` contém informações sensíveis e não deve ser publicado no GitHub.

A chave JWT deve conter **exatamente 32 bytes aleatórios**, codificados em Base64, conforme a validação atual da aplicação.

Para gerar uma chave utilizando PowerShell:

```powershell
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
[Convert]::ToBase64String($bytes)
```

Guarde a chave gerada em seu `.env`.

### Iniciar a aplicação

```powershell
docker compose up -d --build
```

Os serviços são:

| Serviço | Porta local |
|---|---|
| API Spring Boot | 8081 |
| PostgreSQL | 5434 |

A API ficará disponível em:

http://localhost:8081

O banco PostgreSQL utiliza volume persistente para preservar os dados entre reinicializações.

### Verificar os contêineres

```powershell
docker compose ps
```

### Verificar a aplicação

```powershell
Invoke-RestMethod http://localhost:8081/actuator/health
```

Resultado esperado:

```text
status
------
UP
```

### Consultar logs

```powershell
docker compose logs --tail=100 api
```

### Encerrar a aplicação

```powershell
docker compose down
```

Esse comando preserva o volume do PostgreSQL.

**Atenção:** `docker compose down -v` remove os volumes associados e pode apagar os dados locais do banco.

### Habilitar simulação de pagamentos

Por padrão, os endpoints de aprovação e rejeição ficam bloqueados.

Para habilitar a simulação dentro do Docker Compose em um ambiente de desenvolvimento controlado, acrescente ao bloco `environment` do serviço `api` em `compose.yaml`:

```yaml
COMMERCECORE_PAYMENT_SIMULATION_ENABLED: ${COMMERCECORE_PAYMENT_SIMULATION_ENABLED:-false}
```

Depois, configure no `.env`:

```dotenv
COMMERCECORE_PAYMENT_SIMULATION_ENABLED=true
```

Recrie o contêiner da API para aplicar a configuração:

```powershell
docker compose up -d --build
```

**Não habilite essa opção em um ambiente com pagamentos reais.**

## Documentação Swagger

A documentação interativa está disponível em:

- Swagger UI: http://localhost:8081/swagger-ui/index.html
- OpenAPI: http://localhost:8081/v3/api-docs

Os endpoints protegidos exigem autenticação JWT.

## Banco de dados

A aplicação utiliza PostgreSQL 16 e Flyway para gerenciar a estrutura do banco.

### Migrações

| Versão | Descrição |
|---|---|
| V1 | Categorias e produtos |
| V2 | Usuários |
| V3 | Carrinhos e itens |
| V4 | Pedidos e itens |
| V5 | Pagamentos simulados |

O Flyway executa automaticamente as migrações pendentes durante a inicialização da aplicação.

O Hibernate utiliza:

```properties
spring.jpa.hibernate.ddl-auto=validate
```

Assim, o Hibernate valida o mapeamento das entidades sem criar ou modificar automaticamente as tabelas.

### Principais entidades

- User
- Category
- Product
- Cart
- CartItem
- Order
- OrderItem
- Payment

### Integridade e concorrência

O projeto utiliza:

- Chaves primárias e estrangeiras.
- Restrições de unicidade.
- Restrições de integridade no banco.
- Transações Spring.
- Bloqueios pessimistas para operações críticas.
- Ordenação de bloqueios de produtos por identificador.
- Validação de estoque.
- Proteção contra operações duplicadas.

## Testes automatizados

O projeto possui **63 testes automatizados aprovados na última execução local**.

Os testes incluem:

- Cadastro e autenticação.
- Autorização por perfil.
- Catálogo de produtos e categorias.
- Carrinho de compras.
- Checkout.
- Validação e reposição de estoque.
- Rollback de operações.
- Cancelamento de pedidos.
- Pagamentos pendentes.
- Aprovação e rejeição simuladas.
- Prevenção contra pagamento duplicado.
- Restrição de acesso entre clientes.
- Concorrência no checkout.
- Concorrência no cancelamento.
- Concorrência entre aprovação e rejeição.
- Concorrência entre aprovação e cancelamento.
- Segurança dos endpoints demonstrativos.
- Bloqueio da simulação com HTTP 403.
- Rejeição de requisições sem autenticação com HTTP 401.

### Banco de testes

Os testes de integração utilizam um banco separado:

`commercecore_test`

Isso evita modificar os dados do banco principal `commercecore`.

### Executar os testes

Com PostgreSQL disponível e o banco de testes configurado:

```powershell
$env:COMMERCECORE_DB_PASSWORD = "sua_senha_local_do_postgresql"

.\mvnw.cmd clean test
```

Resultado da última execução:

```text
Tests run: 63, Failures: 0, Errors: 0, Skipped: 0

BUILD SUCCESS
```

### Executar testes específicos

Pagamentos:

```powershell
.\mvnw.cmd '-Dtest=PaymentIntegrationTest' test
```

Concorrência de pagamentos:

```powershell
.\mvnw.cmd '-Dtest=PaymentConcurrencyIntegrationTest' test
```

Segurança de pagamentos:

```powershell
.\mvnw.cmd '-Dtest=PaymentSecurityIntegrationTest' test
```

## Integração contínua

O CommerceCore utiliza GitHub Actions para executar a suíte de testes automaticamente em pushes e Pull Requests para a branch `main`.

A pipeline utiliza:

- Java 21.
- Maven.
- PostgreSQL 16.
- Banco de dados de testes.
- Migrações Flyway.
- Testes automatizados.
- Relatórios de execução.

O status da integração contínua pode ser acompanhado pelo badge apresentado no início deste README.

[Consultar execuções do GitHub Actions](https://github.com/calazans-999/commercecore-api/actions)

## Exemplo de uso da API

Os exemplos abaixo utilizam PowerShell e pressupõem que a aplicação esteja em execução em `http://localhost:8081`.

### 1. Registrar um cliente

```powershell
$demoEmail = "demo-" + [Guid]::NewGuid().ToString("N") + "@example.com"

$body = @{
    name = "Cliente Demo"
    email = $demoEmail
    password = "SenhaDemo123!"
} | ConvertTo-Json

Invoke-RestMethod `
    -Uri "http://localhost:8081/api/v1/auth/register" `
    -Method POST `
    -ContentType "application/json" `
    -Body $body
```

### 2. Realizar login

```powershell
$body = @{
    email = $demoEmail
    password = "SenhaDemo123!"
} | ConvertTo-Json

$login = Invoke-RestMethod `
    -Uri "http://localhost:8081/api/v1/auth/login" `
    -Method POST `
    -ContentType "application/json" `
    -Body $body

$token = $login.accessToken

$headers = @{
    Authorization = "Bearer $token"
}
```

O token JWT permite acessar os endpoints protegidos.

Não compartilhe tokens de autenticação em logs públicos ou capturas de tela.

### 3. Listar produtos

```powershell
Invoke-RestMethod `
    -Uri "http://localhost:8081/api/v1/products" `
    -Method GET
```

### 4. Adicionar um produto ao carrinho

O produto precisa existir e possuir estoque disponível.

Substitua o ID abaixo pelo ID de um produto cadastrado:

```powershell
$body = @{
    productId = 1
    quantity = 2
} | ConvertTo-Json

Invoke-RestMethod `
    -Uri "http://localhost:8081/api/v1/cart/items" `
    -Method POST `
    -Headers $headers `
    -ContentType "application/json" `
    -Body $body
```

### 5. Consultar o carrinho

```powershell
Invoke-RestMethod `
    -Uri "http://localhost:8081/api/v1/cart" `
    -Method GET `
    -Headers $headers
```

### 6. Finalizar a compra

```powershell
$order = Invoke-RestMethod `
    -Uri "http://localhost:8081/api/v1/orders/checkout" `
    -Method POST `
    -Headers $headers

$order
```

O checkout valida o estoque, registra o pedido e seus itens, desconta as unidades compradas e esvazia o carrinho.

### 7. Consultar o histórico de pedidos

```powershell
Invoke-RestMethod `
    -Uri "http://localhost:8081/api/v1/orders" `
    -Method GET `
    -Headers $headers
```

### 8. Criar um pagamento simulado

Utilize o ID do pedido gerado no checkout:

```powershell
$orderId = $order.id

$payment = Invoke-RestMethod `
    -Uri "http://localhost:8081/api/v1/orders/$orderId/payment" `
    -Method POST `
    -Headers $headers

$payment
```

O pagamento será criado com status `PENDING`.

### 9. Consultar o pagamento

```powershell
Invoke-RestMethod `
    -Uri "http://localhost:8081/api/v1/orders/$orderId/payment" `
    -Method GET `
    -Headers $headers
```

### 10. Simular aprovação

Este endpoint exige que a simulação esteja habilitada explicitamente no ambiente.

```powershell
Invoke-RestMethod `
    -Uri "http://localhost:8081/api/v1/orders/$orderId/payment/approve" `
    -Method POST `
    -Headers $headers
```

Com a simulação desabilitada, a API retorna HTTP 403.

### 11. Simular rejeição

Utilize outro pagamento ainda `PENDING`, pois um pagamento já processado não pode ser modificado novamente.

```powershell
Invoke-RestMethod `
    -Uri "http://localhost:8081/api/v1/orders/$orderId/payment/reject" `
    -Method POST `
    -Headers $headers
```

Esse endpoint também exige que a simulação esteja habilitada.

### 12. Cancelar um pedido

Para pedidos elegíveis:

```powershell
Invoke-RestMethod `
    -Uri "http://localhost:8081/api/v1/orders/$orderId/cancel" `
    -Method POST `
    -Headers $headers
```

Pedidos com pagamentos aprovados não podem ser cancelados diretamente.

Pedidos já cancelados retornam HTTP 409.

## Segurança

O projeto implementa:

- Autenticação JWT.
- Senhas protegidas com BCrypt.
- Controle de acesso baseado em perfis.
- Validação de contas habilitadas.
- Autorização de operações por proprietário.
- Proteção de endpoints administrativos.
- Configuração de JWT por variável de ambiente.
- API stateless.
- Bloqueio padrão das decisões simuladas de pagamento.
- Transações e bloqueios para operações críticas.

### Limitações atuais

O CommerceCore é um projeto de portfólio e ainda não representa um sistema comercial pronto para produção.

Entre os pontos que precisam de evolução estão:

- Integração com gateway de pagamento real.
- Validação de notificações autenticadas do gateway.
- Processamento de estornos.
- Rate limiting de endpoints sensíveis.
- Tratamento padronizado de erros.
- Auditoria de operações críticas.
- Observabilidade e monitoramento.
- Configuração HTTPS para implantação pública.
- Revisão de segurança e testes de carga.

## Melhorias futuras

- Tratamento global de exceções.
- Padronização das respostas de erro.
- Auditoria de pagamentos.
- Estorno de pagamentos.
- Integração real com gateway de pagamentos.
- Rate limiting.
- Métricas e observabilidade.
- Deploy em nuvem.
- Pipeline de entrega contínua.
- Documentação adicional de arquitetura.

## Autor

**Marcus Vinicius de Souza Calazans**

Projeto de portfólio — Desenvolvimento Backend Java.

[Repositório GitHub](https://github.com/calazans-999/commercecore-api)
