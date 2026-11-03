
# CommerceCore API
[![CommerceCore CI](https://github.com/calazans-999/commercecore-api/actions/workflows/ci.yml/badge.svg)](https://github.com/calazans-999/commercecore-api/actions/workflows/ci.yml)

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.2-green)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue)
![Docker](https://img.shields.io/badge/Docker-Compose-blue)

Backend de e-commerce desenvolvido com Java 21, Spring Boot 4.1.2, PostgreSQL e Docker.

## Sobre o projeto

O CommerceCore é uma API REST que simula as principais operações de uma plataforma de comércio eletrônico.

O sistema permite cadastrar produtos, autenticar usuários, gerenciar carrinhos e finalizar compras com controle de estoque.

## Tecnologias

- Java 21
- Spring Boot 4.1.2
- Spring Security e JWT
- Spring Data JPA e Hibernate
- PostgreSQL 16
- Flyway
- Maven
- Docker e Docker Compose
- Swagger / OpenAPI
- JUnit 5, Mockito e MockMvc

## Arquitetura

A aplicação utiliza arquitetura em camadas:

Controller -> Service -> Repository -> PostgreSQL

Principais pacotes:

- controller: endpoints REST
- service: regras de negócio
- repository: persistência
- entity: entidades JPA
- dto: contratos da API
- config: configurações
- security: autenticação e autorização

## Funcionalidades

### Autenticação
- Cadastro de usuários
- Login com JWT
- Criptografia de senhas com BCrypt
- Perfis CUSTOMER e ADMIN

### Produtos e categorias
- Cadastro e consulta
- Atualização de informações
- Desativação de produtos
- Paginação e filtros

### Carrinho
- Adicionar produtos
- Atualizar quantidades
- Remover produtos
- Esvaziar carrinho
- Calcular valor total

### Pedidos
- Finalizar compras
- Verificar estoque disponível
- Descontar estoque
- Registrar preços históricos
- Consultar pedidos do cliente
- Impedir acesso aos pedidos de outros usuários
- Cancelamento de pedidos pelo próprio cliente
- Reposição automática de estoque após cancelamento
- Proteção contra cancelamentos duplicados (HTTP 409)
- Bloqueio pessimista para operações concorrentes
- Transações para manter a consistência entre pedido e estoque

## Endpoints principais

| Método | Endpoint | Descrição |
|---|---|---|
| POST | /api/v1/auth/register | Registrar usuário |
| POST | /api/v1/auth/login | Realizar login |
| GET | /api/v1/auth/me | Consultar perfil |
| GET | /api/v1/products | Listar produtos |
| GET | /api/v1/categories | Listar categorias |
| GET | /api/v1/cart | Consultar carrinho |
| POST | /api/v1/cart/items | Adicionar produto |
| PUT | /api/v1/cart/items/{itemId} | Alterar quantidade |
| DELETE | /api/v1/cart/items/{itemId} | Remover produto |
| DELETE | /api/v1/cart | Esvaziar carrinho |
| POST | /api/v1/orders/checkout | Finalizar compra |
| GET | /api/v1/orders | Listar pedidos |
| GET | /api/v1/orders/{id} | Consultar pedido |
| POST | /api/v1/orders/{id}/cancel | Cancelar pedido e repor estoque |

## Executando com Docker

### Requisitos

- Docker Desktop
- Docker Compose

### Configuração

Copie o arquivo `.env.example` para `.env`:

```powershell
Copy-Item .env.example .env
```

Configure as variáveis `POSTGRES_PASSWORD` e `COMMERCECORE_JWT_SECRET`.

A chave JWT deve conter pelo menos 32 bytes aleatórios codificados em Base64.

### Iniciar a aplicação

```powershell
docker compose up -d --build
```

A API ficará disponível em:

http://localhost:8081

### Verificar a aplicação

```powershell
Invoke-RestMethod http://localhost:8081/actuator/health
```

Resultado esperado: `UP`.

### Encerrar a aplicação

```powershell
docker compose down
```

Esse comando preserva o volume do PostgreSQL.

## Documentação Swagger

- Swagger UI: http://localhost:8081/swagger-ui.html
- OpenAPI: http://localhost:8081/v3/api-docs

## Banco de dados

O banco PostgreSQL utiliza quatro migrações Flyway:

- V1: categorias e produtos
- V2: usuários
- V3: carrinhos e itens
- V4: pedidos e itens

## Testes automatizados

O projeto possui 49 testes automatizados aprovados na última execução.

Os testes cobrem autenticação, autorização, catálogo, carrinho, pedidos, checkout, validação de estoque, rollback e concorrência.

Para executar os testes, configure o banco separado `commercecore_test` e utilize:

```powershell
.\mvnw.cmd clean test
```

## Melhorias futuras

- Gateway de pagamentos
- Cancelamento de pedidos
- GitHub Actions para CI/CD
- Monitoramento e métricas
- Deploy em nuvem


## Exemplo de uso da API

Os exemplos abaixo utilizam PowerShell e pressupõem que a aplicação
esteja em execução em http://localhost:8081.

### 1. Registrar um cliente

```powershell
$body = @{
    name = "Cliente Demo"
    email = "cliente.demo@example.com"
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
    email = "cliente.demo@example.com"
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

### 3. Listar produtos

```powershell
Invoke-RestMethod `
    -Uri "http://localhost:8081/api/v1/products" `
    -Method GET
```

### 4. Adicionar um produto ao carrinho

O produto precisa existir e possuir estoque disponível.
Substitua o ID abaixo pelo ID de um produto cadastrado.

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
Invoke-RestMethod `
    -Uri "http://localhost:8081/api/v1/orders/checkout" `
    -Method POST `
    -Headers $headers
```

O checkout valida o estoque, registra o pedido e seus itens,
desconta as unidades compradas e esvazia o carrinho.

### 7. Consultar o histórico de pedidos

```powershell
Invoke-RestMethod `
    -Uri "http://localhost:8081/api/v1/orders" `
    -Method GET `
    -Headers $headers
```

## Integração contínua

O projeto utiliza GitHub Actions para executar testes automaticamente
em pushes e Pull Requests para a branch main.

A pipeline configura Java 21 e PostgreSQL 16, executa as migrações
Flyway e roda os testes automatizados com Maven.

Os relatórios de testes ficam disponíveis como artefatos das execuções.

[Consultar execuções do GitHub Actions](https://github.com/calazans-999/commercecore-api/actions)

## Autor

Marcus Vinicius de Souza Calazans

Projeto de portfólio — Desenvolvimento Backend Java.
