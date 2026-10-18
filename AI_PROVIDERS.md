# J.A.V.E.S. — Provedores de IA

## Estado atual (0.3.0)
- **Gemini**: implementado (`GeminiProvider.kt`)
- **Groq**: implementado (`OpenAiCompatibleProvider` / `createGroqProvider`)
- **OpenRouter**: implementado (`OpenAiCompatibleProvider` / `createOpenRouterProvider`)

Nenhuma chave de API está no código ou no ZIP. Cada provedor usa a chave salva no aparelho (Android Keystore + AES-GCM).

## Configurar
1. Gere a chave no site do provedor.
2. No app: CONFIG → escolha o provedor → cole a chave → SALVAR → TESTAR CONEXÃO.
3. Modelo padrão (editável em CONFIG):
   - Gemini: `gemini-2.0-flash`
   - Groq: `llama-3.3-70b-versatile`
   - OpenRouter: `openai/gpt-4o-mini`

## Endpoints (sem segredos)
- Gemini: `https://generativelanguage.googleapis.com/v1beta/models/{modelo}:generateContent` (header `x-goog-api-key`)
- Groq: `https://api.groq.com/openai/v1/chat/completions` (Bearer)
- OpenRouter: `https://openrouter.ai/api/v1/chat/completions` (Bearer)

## Falhas tratadas
Chave ausente/inválida, modelo inexistente, rate limit, rede, timeout, resposta vazia, erro de servidor — mensagens em português sem vazar a chave.
