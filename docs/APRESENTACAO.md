# Roteiro de apresentação do ValidaDoc

Use o modo `LOCAL` para a demonstração sem chave de API. Inicie pelo `INICIAR.bat`, abra http://localhost:8080 e deixe a aplicação pronta antes de começar. O [README](../README.md) contém os comandos completos.

## 1. Explique o problema — 30 segundos

“O ValidaDoc recebe um comprovante, lê o conteúdo e verifica se os dados atendem às regras do exercício. A pessoa vê os campos encontrados e o motivo de aprovação ou reprovação, sem precisar analisar uma resposta técnica da API.”

Mostre a tela de envio, os formatos permitidos e o campo obrigatório de titular.

## 2. Mostre o fluxo completo — 1 minuto

1. Clique em **Baixar exemplo**. O documento é fictício e tem data de emissão atual.
2. Preencha o titular com **Ana Silva**.
3. Envie o PDF e processe.
4. Mostre os campos extraídos e cada regra avaliada.
5. Abra **Ver texto extraído do documento** e compare o texto com os campos.
6. Abra **Meus envios** e consulte o registro salvo.

“Este PDF tem texto selecionável, então o PDFBox consegue fazer a leitura direta. Quando recebemos uma imagem ou uma página digitalizada, entra o fluxo de OCR.”

Não apresente o caminho PDFBox como uma execução de OCR. O campo de método de extração informa qual caminho foi utilizado.

## 3. Demonstre uma reprovação — 30 segundos

Faça um novo envio do mesmo exemplo, mas informe **Maria Souza** como titular. Mostre a regra de titularidade reprovada e o motivo.

“A extração pode estar correta e o documento ainda não atender a uma regra. Aqui o nome do documento é Ana Silva, diferente do nome que informei.”

## 4. Explique o papel de cada tecnologia — 1 minuto

| Tecnologia | Papel neste projeto |
| --- | --- |
| HTML, CSS e JavaScript | Tela de upload, apresentação do resultado e navegação no histórico. |
| Java e Spring Boot | API, orquestração do processamento e regras de negócio. |
| PDFBox | Lê texto de PDFs e renderiza páginas digitalizadas quando necessário. |
| OpenCV | Converte imagens para cinza, reduz ruído e faz a binarização. |
| Tesseract | Reconhece os caracteres e devolve texto em português. |
| Extrator local | Identifica campos por rótulos e expressões regulares, sem serviço de IA. |
| OpenAI opcional | Recebe o texto e devolve os campos em JSON estruturado. |
| JPA com H2 ou PostgreSQL | Salva resultados e permite consulta posterior. |

“O OCR produz texto. A extração organiza o texto em campos. A validação decide se os campos atendem às regras. São responsabilidades separadas.”

## 5. Mostre o código que sustenta a apresentação — 1 minuto

Abra estes arquivos na ordem desejada:

- [`DocumentoController.java`](../src/main/java/br/com/mentoria/validadoc/api/DocumentoController.java): recebe arquivo e titular, devolve resultado e expõe o histórico.
- [`DocumentTextExtractor.java`](../src/main/java/br/com/mentoria/validadoc/ocr/DocumentTextExtractor.java): seleção entre PDF textual e OCR, limites e leitura das imagens.
- [`LocalFieldExtractor.java`](../src/main/java/br/com/mentoria/validadoc/domain/LocalFieldExtractor.java): busca de rótulos, CPF e datas, preservando `null` quando há ausência ou ambiguidade.
- [`DadosDocumento.java`](../src/main/java/br/com/mentoria/validadoc/domain/DadosDocumento.java): contrato dos campos extraídos.
- [`DocumentValidator.java`](../src/main/java/br/com/mentoria/validadoc/domain/DocumentValidator.java): regras com relógio injetável para testar as datas.
- [`DocumentoService.java`](../src/main/java/br/com/mentoria/validadoc/service/DocumentoService.java): reúne etapas, exige confiança mínima quando há OCR e salva o resultado.
- [`OpenAiFieldExtractor.java`](../src/main/java/br/com/mentoria/validadoc/service/OpenAiFieldExtractor.java): integração opcional com Responses API e JSON Schema.

## Perguntas que podem aparecer

**Por que é possível rodar sem IA?**

O extrator local usa rótulos previsíveis como `Nome:` e `CPF:`. Isso facilita estudar e demonstrar o fluxo. Layouts desconhecidos podem exigir ajuste das regras ou outro extrator. O modo local não é anunciado como IA.

**Como a IA devolve um objeto Java?**

O servidor solicita um JSON com seis campos e um esquema estrito. A resposta é interpretada e convertida para `DadosDocumento`. Campos ausentes devem ser `null`. A decisão final continua nas regras Java; o esquema controla o formato, não a veracidade dos dados. A [documentação oficial da OpenAI](https://developers.openai.com/api/docs/guides/structured-outputs?api-mode=responses) explica Structured Outputs.

**Qual a diferença entre 3 meses e 90 dias?**

A implementação usa 90 dias corridos, incluindo o dia de hoje e o dia exatamente 90 dias atrás. Datas futuras reprovam. Meses do calendário têm durações diferentes, por isso foi adotado um limite explícito.

**O CPF é validado?**

É verificada a presença de 11 dígitos. Os dígitos verificadores não são conferidos, conforme a regra de presença do material. O CPF da amostra é fictício.

**Como funciona a comparação do nome?**

O nome informado é obrigatório. A comparação ignora diferenças de acento, caixa e espaços repetidos, mas não aceita abreviações como equivalentes. Isso compara textos; não verifica a identidade da pessoa.

**O que acontece com uma imagem ruim?**

Ela pode gerar campos ausentes ou incoerentes. Quando há OCR, a menor confiança média entre as páginas precisa ser de pelo menos 60%. Esse número é um indicador da leitura, não de autenticidade. O projeto não corrige automaticamente rotação ou perspectiva.

**Onde os dados ficam?**

No padrão, o H2 salva o texto e os resultados em `./data`. É possível usar PostgreSQL pelo perfil `postgres`. Os arquivos originais não são mantidos como documentos. O histórico consulta os 100 resultados mais recentes, sem apagar os anteriores por esse limite.

**Pode ser usado em produção?**

Esta entrega é local e voltada ao ensino. Não tem autenticação, verificação de autenticidade, consulta a emissores ou gestão de acesso para múltiplos usuários. A aplicação escuta em `127.0.0.1` por padrão. Use amostras fictícias durante a apresentação.

**O que já pode ser demonstrado sem serviços externos?**

Tela, upload, leitura de PDF, OCR com as dependências preparadas, extração local, validação e histórico em H2. Chamadas reais à OpenAI dependem de chave, acesso e créditos. PostgreSQL depende de banco configurado. Não apresente essas integrações como testadas no ambiente sem executá-las.

## Antes de apresentar

Execute os testes conforme o README, confirme que o exemplo atual processa e deixe uma reprovação por titular divergente pronta no histórico. Se quiser mostrar OCR, use uma imagem nítida de um documento fictício com os rótulos esperados e confira o resultado antes. Evite depender de internet ou de uma chave ainda não configurada durante a demonstração.
