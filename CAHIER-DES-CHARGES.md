# spring-ai-agents — Cahier des Charges

**Version :** 1.1 (révisée après revue d'architecture)
**Date :** Avril 2026
**Statut :** draft

---

## 1. Objectif

Développer un framework Java d'orchestration multi-agents au-dessus de Spring AI, permettant :

- la coordination d'agents spécialisés
- la délégation de tâches
- l'exécution structurée de workflows LLM
- l'intégration native avec Spring Boot (auto-configuration, injection, properties)

---

## 2. Positionnement

| Aspect | Décision |
|--------|----------|
| Type de projet | Bibliothèque indépendante (non fork) |
| Dépendance | Spring AI (spring-ai-client-chat) |
| Rôle | Couche d'orchestration (control plane) |
| Non-objectif | Remplacer Spring AI |
| Non-objectif | Être un provider LLM |
| Inspiration | LangGraph (graph), CrewAI (squad), AWS Strands (patterns) |

---

## 3. Périmètre fonctionnel — V1

### 3.1 Modèle cœur

**Agent**

```java
public interface Agent {
    AgentResult execute(AgentContext context);

    default Flux<AgentEvent> executeStream(AgentContext context) {
        return Flux.just(AgentEvent.completed(execute(context)));
    }
}
```

**AgentContext** — state typé, pas de `Map<String, Object>` exposée

```java
public final class AgentContext {
    private final List<Message> messages;
    private final StateBag state;

    public <T> T get(StateKey<T> key);
    public <T> AgentContext with(StateKey<T> key, T value);
}

public record StateKey<T>(String name, Class<T> type) {}
```

**AgentResult** — riche, couvre les cas réels (texte, tool calls, JSON structuré)

```java
public record AgentResult(
    @Nullable String text,
    List<ToolCall> toolCalls,
    @Nullable Object structuredOutput,
    Map<StateKey<?>, Object> stateUpdates,
    boolean completed,
    @Nullable AgentError error
) {}
```

**AgentEvent** — pour le streaming d'étapes intermédiaires

```java
public sealed interface AgentEvent {
    record Token(String chunk) implements AgentEvent {}
    record ToolCallStart(String toolName, Map<String, Object> args) implements AgentEvent {}
    record ToolCallEnd(String toolName, Object result) implements AgentEvent {}
    record NodeTransition(String from, String to) implements AgentEvent {}
    record Completed(AgentResult result) implements AgentEvent {}
}
```

### 3.2 Orchestration — Graph

Concepts :
- `Node` = un `Agent`
- `Edge` = transition typée (directe ou conditionnelle)
- `AgentGraph` = exécution orchestrée avec état partagé

**API**

```java
public interface Node {
    String name();
    AgentResult execute(AgentContext context);
}

public sealed interface Edge {
    record Direct(String from, String to) implements Edge {}
    record Conditional(String from, Predicate<AgentContext> when, String to) implements Edge {}
}

public final class AgentGraph {
    public static Builder builder() { ... }
    public AgentResult invoke(AgentContext initial);
    public Flux<AgentEvent> invokeStream(AgentContext initial);
}
```

**Capacités V1**
- Enchaînement de nœuds
- Cycles autorisés (boucles ReAct, retry, self-reflection)
- Transitions conditionnelles via `Predicate<AgentContext>`
- État partagé via `AgentContext` (immutable, passé de nœud en nœud)
- `ErrorPolicy` par graphe (`FAIL_FAST`, `RETRY_ONCE`, `SKIP_NODE`)

### 3.3 Rôles d'agents

**CoordinatorAgent** — planification et routage

```java
CoordinatorAgent coordinator = CoordinatorAgent.builder()
    .chatClient(chatClient)
    .systemPrompt("...")
    .executors(Map.of(
        "research", researchExecutor,
        "analysis", analysisExecutor,
        "writing", writingExecutor
    ))
    .routingStrategy(RoutingStrategy.LLM_DRIVEN)
    .build();
```

Responsabilités :
- Découpe une requête en sous-tâches
- Sélectionne l'executor approprié (stratégie pluggable)
- Agrège les résultats

**ExecutorAgent** — exécution spécialisée

```java
ExecutorAgent researchExecutor = ExecutorAgent.builder()
    .chatClient(chatClient)
    .systemPrompt("You are a research specialist...")
    .tools(ToolCallbacks.from(new SearchService(), new WikipediaService()))
    .build();
```

Responsabilités :
- Exécute une tâche unique
- Appelle `ChatClient` (passé en dépendance, pas couplage dur)
- Utilise les tools déclarés

### 3.4 Intégration Spring

- Auto-configuration Spring Boot via `spring-ai-agents-starter`
- Injection de `ChatClient` dans les agents
- Configuration via `application.yml` (voir annexe A)
- Compatibilité avec `ChatMemory` existant

### 3.5 Observabilité minimale (V1)

**Obligatoire dès V1** pour permettre le debug en production.

- Logs SLF4J structurés par nœud (entrée/sortie, durée, erreurs)
- Métriques Micrometer :
  - `agents.execution.count{agent,status}`
  - `agents.execution.duration{agent}`
  - `agents.graph.transitions{graph,from,to}`
  - `agents.llm.calls{provider,model}`
- Hooks `AgentListener` pour instrumentation custom

Le tracing OpenTelemetry complet est en V3, mais les spans basiques (par nœud) sont émis via Micrometer Observation API — compatible OTel sans l'implémenter.

### 3.6 Gestion d'erreurs

```java
public enum ErrorPolicy {
    FAIL_FAST,       // propage l'erreur, arrête le graph
    RETRY_ONCE,      // retry le nœud une fois, puis fail
    SKIP_NODE        // loggue et continue
}

public record AgentError(
    String nodeName,
    Throwable cause,
    int retryCount
) {}
```

### 3.7 Testabilité

- `MockAgent` — agent prédéfini pour tests unitaires
- `TestGraph` — runner qui vérifie les transitions sans appel LLM réel
- Fixtures documentées pour JUnit 5

---

## 4. Périmètre technique

### 4.1 Contraintes

- Java 17+
- Spring Boot 3.x
- Spring AI (version alignée sur la dernière stable)
- Aucune duplication avec Spring AI (on consomme, on n'étend pas `ChatClient`)

### 4.2 Architecture modulaire

```
spring-ai-agents/
├── spring-ai-agents-core/         Agent, AgentContext, AgentResult, AgentEvent, StateKey/StateBag
├── spring-ai-agents-graph/        AgentGraph, Node, Edge, ErrorPolicy
├── spring-ai-agents-squad/        CoordinatorAgent, ExecutorAgent, RoutingStrategy
├── spring-ai-agents-test/         MockAgent, TestGraph, fixtures JUnit
└── spring-ai-agents-starter/      Auto-configuration Spring Boot
```

---

## 5. Hors périmètre — V1

- Checkpointing / persistence (V2)
- Human-in-the-loop natif (V3)
- Tracing OpenTelemetry complet (V3)
- UI / visualisation de graphe
- Multi-agent distribué (RPC / réseau)
- Memory long terme (semantic, épisodique)

---

## 6. Évolution

### V2

- Squad étendue : crew de rôles configurables (inspiration CrewAI)
- Checkpointing pluggable : `InMemoryCheckpointer`, `JdbcCheckpointer`
- Streaming enrichi : plus d'événements intermédiaires

### V3

- Interrupt / Resume natif (HITL)
- Tracing OTel complet (conventions sémantiques GenAI)
- Compatibilité MCP enrichie
- Agent-as-tool (un graph exposé comme tool)

---

## 7. Principes de design API

1. **Séparation stricte** : orchestration ≠ exécution LLM. `AgentGraph` ne connaît pas `ChatClient`, `ExecutorAgent` si.
2. **Composition fonctionnelle d'abord** : `Agent` = interface, pas annotation. Annotations optionnelles en V2.
3. **State typé** : `StateKey<T>` partout, pas de `Map<String, Object>` exposée en API publique.
4. **Immutabilité** : `AgentContext` est immutable, chaque nœud retourne un nouveau context.
5. **Explicit over magic** : pas de scan classpath, tout est déclaratif.
6. **Streaming first-class** : toute méthode `execute` a son pendant `executeStream`.

---

## 8. Cas d'usage cible — V1

**Research Squad**

```
User ──> CoordinatorAgent
         │
         ├──> ExecutorAgent (research)    → search tool
         ├──> ExecutorAgent (analysis)    → LLM reasoning
         └──> ExecutorAgent (writing)     → LLM generation
              │
              └──> AgentResult (rapport structuré)
```

Implémenté comme un `AgentGraph` :

```java
AgentGraph researchGraph = AgentGraph.builder()
    .addNode("coordinate", coordinator)
    .addNode("research", researchExecutor)
    .addNode("analyze", analysisExecutor)
    .addNode("write", writingExecutor)
    .addEdge("coordinate", "research")
    .addEdge("research", "analyze")
    .addEdge("analyze", "write")
    .errorPolicy(ErrorPolicy.RETRY_ONCE)
    .build();

AgentResult result = researchGraph.invoke(
    AgentContext.of(UserMessage.of("Compare Claude 4 and GPT-5"))
);
```

---

## 9. Critères de succès

### Technique

- API ≤ 10 concepts publics principaux (Agent, Context, Result, Event, Node, Edge, Graph, StateKey, ErrorPolicy, StateBag)
- Temps de prise en main < 30 minutes (cold-start to running example)
- Zéro boucle `while` côté utilisateur
- Couverture de tests ≥ 80% sur le `core` et `graph`
- Chaque module a un test d'intégration exécuté en CI

### Produit

- 1 exemple complet fonctionnel (Research Squad)
- Documentation : Getting Started, Concepts, 3 recipes (ReAct loop, supervisor pattern, parallel executors)
- Intégration Spring fluide (starter fonctionnel en 5 lignes de `application.yml`)

### Adoption

- Issue GitHub de référence (#5826 sur spring-ai)
- Publication GitHub public avec MIT/Apache-2.0
- Objectif 6 mois : 100 stars, 3 contributeurs externes

---

## 10. Stratégie de lancement

1. Publier le projet (GitHub public)
2. Conserver l'issue spring-ai#5826 comme design de référence
3. Livrer V1 minimaliste fonctionnelle (4-6 semaines)
4. Publier un blog technique avec l'exemple Research Squad
5. Poster sur les canaux Spring (`spring-projects` slack, r/java, LinkedIn)
6. Collecter retours avant de lancer V2

---

## 11. Décisions structurantes

| Décision | Choix |
|----------|-------|
| Démarrer par quoi ? | `core` + `graph` (Nodes, Edges, runtime) |
| Puis quoi ? | `squad` (Coordinator + Executor) |
| Streaming V1 ? | **Oui**, mode basique obligatoire |
| Obs V1 ? | **Oui**, Micrometer obligatoire |
| Error handling V1 ? | **Oui**, via `ErrorPolicy` |
| Checkpointing V1 ? | Non, reporté V2 |
| HITL V1 ? | Non, reporté V3 |
| Tracing OTel complet V1 ? | Non, reporté V3 |

---

## 12. Annexes

### Annexe A — Configuration `application.yml`

```yaml
spring:
  ai:
    agents:
      enabled: true
      default-error-policy: RETRY_ONCE
      observability:
        metrics: true
        events: true
      squad:
        default-routing-strategy: LLM_DRIVEN
```

### Annexe B — Dépendances Maven

```xml
<dependency>
    <groupId>io.github.asekka</groupId>
    <artifactId>spring-ai-agents-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

### Annexe C — Glossaire

| Terme | Définition |
|-------|-----------|
| Agent | Unité d'exécution autonome qui transforme un contexte en résultat |
| Node | Un Agent intégré dans un Graph |
| Edge | Transition entre deux Nodes, éventuellement conditionnelle |
| Graph | Composition orchestrée de Nodes et Edges |
| Coordinator | Agent qui planifie et délègue aux Executors |
| Executor | Agent spécialisé qui exécute une tâche unique |
| State | Conteneur typé partagé entre les Nodes d'un Graph |
| ErrorPolicy | Stratégie de gestion d'erreur au niveau Graph |

---

## 13. Changelog

| Version | Date | Changements |
|---------|------|-------------|
| 1.0 | Avril 2026 | Draft initial |
| 1.1 | Avril 2026 | Typage strict du state (StateBag vs Map), AgentResult enrichi (toolCalls, structuredOutput), observabilité minimale et error policy déplacés en V1, streaming ajouté, testabilité en critère de succès |
