# Evolução semanal — ValidaDoc

## Ponto de partida

O objetivo da atividade é acompanhar o desenvolvimento e o aprendizado ao longo do semestre. A implementação inicial foi adiantada com assistência de IA e publicada em 26/10/2026. Ela é um protótipo para estudo e revisão, não evidência de evolução semanal anterior.

Em 27/10/2026, foi organizado este plano. A branch `prototipo-inicial` preserva o commit `1fea34ee34d6d949e2107a5bc57bbd4767374674`. O histórico permanece disponível. A `main` continua com a base funcional; não houve remoção de funcionalidades nem reconstrução de datas de commits.

Este é um planejamento proposto, a ajustar ao calendário e às orientações do professor. Não há datas de entrega presumidas nem execução automática das próximas etapas.

## Ciclos propostos

| Ciclo | Objetivo | Trabalho real a realizar | Evidência para apresentar |
| --- | --- | --- | --- |
| Próxima aula | Entender e revisar o ponto de partida | Rodar a aplicação, explicar controller → service → extrator → validador, comparar os dois contratos HTTP e anotar dúvidas sobre versões/regras com o professor | Notas de estudo próprias e decisões da aula; corrigir somente o que for identificado |
| Semana seguinte | Melhorar a extração local | Selecionar amostras fictícias com layouts diferentes, identificar falhas reproduzíveis e corrigir um caso de cada vez | Amostra de falha, teste que falha antes e passa depois, explicação da mudança |
| Ciclo 3 | Verificar persistência real | Executar com PostgreSQL, validar persistência após reinício e tratar problemas encontrados | Teste de integração ou roteiro reproduzível e registro do resultado; serviço ainda não foi validado |
| Ciclo 4 | Avaliar qualidade do OCR | Comparar imagens fictícias nítidas, inclinadas e com baixo contraste; escolher uma melhoria baseada nos resultados | Comparação antes/depois e limites observados; correção automática de rotação ainda não existe |
| Ciclo 5 | Validar integração com IA | Se houver chave e autorização de uso, testar com amostras fictícias; conferir campos, erros de serviço e custo. Sem chave, aprimorar testes simulados a partir de falhas identificadas | Resultados reais separados dos simulados, sem publicar chave ou dados pessoais |
| Ciclo final, a definir | Consolidar a entrega | Revisar requisitos com o professor, resolver pendências selecionadas e preparar apresentação do código compreendido | Checklist de requisitos, testes, limitações e demonstração pelo aluno |

Não é necessário implementar todos os itens desta proposta. O professor pode priorizar outros pontos. Funcionalidades já existentes não devem ser reapresentadas como novas para preencher semanas.

## Como registrar cada avanço

1. Escolher um problema ou melhoria pequena e escrever o resultado esperado.
2. Entender os arquivos envolvidos antes de alterá-los.
3. Fazer a mudança e verificar o comportamento relevante.
4. Criar um commit descritivo quando houver uma alteração real. Um estudo pode gerar documentação real, mas não exige commit vazio.
5. Registrar abaixo o que foi aprendido, o que foi testado e o que permanece pendente.

Não deixar mudanças prontas escondidas para publicá-las como se tivessem sido desenvolvidas depois. Datas e autoria dos registros devem refletir o trabalho efetivo.

## Registro de acompanhamento

### 27/10/2026 — Organização do acompanhamento

- Protótipo existente preservado em uma branch de referência.
- README esclarece o estado da base e a assistência de IA na preparação inicial.
- Plano semanal proposto, sujeito à revisão em aula.
- Nenhuma nova funcionalidade implementada nesta etapa.
- Próxima ação: executar o exemplo JSON e explicar o caminho até a decisão de aprovação, anotando dúvidas para o professor.

### 29/10/2026 — Data de emissão brasileira com hífen

- Problema reproduzido: `Data de emissão: 29-10-2026` retornava `null` no extrator local, embora a regra de evidência já reconhecesse essa representação.
- Foram adicionados quatro testes. Antes da correção, dois falharam: extração com hífen e repetição da mesma data em formatos diferentes.
- Correção em `LocalFieldExtractor`: reconhecer `dd-MM-aaaa`, distinguir o formato ISO e normalizar o separador brasileiro antes da conversão estrita em `LocalDate`.
- Verificação após a correção: suíte completa com 78 testes aprovados, sem falhas ou testes ignorados (`mvn test`).
- Casos cobertos: data válida, ano bissexto, dia impossível, separadores misturados, datas equivalentes ou conflitantes e vencimento que não deve ser interpretado como emissão.
- Trabalho realizado com assistência de IA. Este registro descreve a alteração técnica; a compreensão pelo aluno deve ser praticada na explicação abaixo.
- Para explicar na aula: a expressão regular reconhece o formato; `LocalDate` verifica se o dia existe no calendário. Aceitar outro separador não significa aceitar qualquer data.
- Exercício de compreensão: explicar por que `29-02-2024` é válido e `29-02-2025` não, e localizar no código a distinção entre formato brasileiro e ISO.
- Próximo passo: revisar esta mudança e levar dúvidas à aula antes de escolher outra melhoria.

### 29/10/2026 — Separação do rótulo CPF/CNPJ na mesma linha

- Problema reproduzido: em `Nome: Ana Silva CPF/CNPJ: 12345678900`, o nome extraído incluía o rótulo e o número. O CPF era reconhecido, mas a separação dos campos não reconhecia o rótulo compartilhado.
- Três testes novos falharam antes da correção: CPF completo, rótulo com espaços (`CPF / CNPJ`) contendo CNPJ e CPF mascarado.
- Correção: extrair a definição de `CPF_LABEL` para uma constante reutilizada tanto na leitura do CPF quanto na separação de campos na mesma linha.
- Verificação após a correção: suíte completa com 81 testes aprovados, sem falhas ou testes ignorados (`mvn test`).
- A leitura de CPF continua exigindo 11 dígitos: CNPJ de 14 dígitos e CPF mascarado permanecem ausentes, enquanto o nome é preservado corretamente.
- Trabalho realizado com assistência de IA. Ponto para estudar: reconhecer um rótulo e validar seu valor são tarefas diferentes; a mesma definição de rótulo deve ser usada pelas duas etapas.

### Modelo para os próximos registros

Copiar somente quando a atividade ocorrer e preencher com o resultado real:

```text
Data:
Objetivo:
O que estudei e consigo explicar:
Problema identificado:
Alteração realizada (se houver):
Verificação e resultado:
Commit ou PR (se houver):
Dúvidas para a aula:
Próximo passo:
```
