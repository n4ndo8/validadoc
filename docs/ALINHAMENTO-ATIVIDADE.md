# Alinhamento com a atividade do professor

Referência consultada: [README de KleberMeira/analise-documento-ia](https://github.com/KleberMeira/analise-documento-ia/blob/main/README.md).
Blob do README consultado: `bc2f299a024ac5f524d895dfe45de91c9e11eafb`.

## Entrega e acompanhamento

- Repositório criado pelo aluno: [n4ndo8/validadoc](https://github.com/n4ndo8/validadoc).
- Convidar `KleberMeira` como colaborador e acompanhar o aceite.
- Registrar alterações reais com commits descritivos para acompanhar a evolução.
- O código já desenvolvido é uma base inicial existente. O primeiro commit deve registrar essa base honestamente, sem datas retroativas ou apresentação de etapas fictícias.
- O projeto será desenvolvido ao longo do semestre e usado como entregável final.

## Comparação com a base atual

| Referência do professor | Implementação atual | Ação a acompanhar |
| --- | --- | --- |
| Java e Spring Boot | Spring Boot 3.5.16, compilação para Java 17; executado localmente com JDK 25 | O exemplo do professor usa Java 21 e Boot 3.5.5. Confirmar se versões exatas são exigidas antes de trocar. |
| OpenCV → Tesseract → interpretação → regras Java | Fluxo real implementado, com PDFBox para PDFs textuais | Preservar e explicar a otimização para PDFs com camada textual. |
| `GET /api/documentos/exemplo` retorna payload JSON | Implementado com dados fictícios e data atual; download PDF mantido | Concluído. |
| `POST /api/documentos/validar` aceita JSON | Adaptador implementado para `nomeArquivo`, `mimeType`, `conteudoDocumento` e `imagemBase64`; multipart preservado | Concluído. |
| Resposta com `etapas`, `dadosExtraidos` e `resultado` | Novo endpoint retorna esse formato, com CPF formatado e cidade/UF | Concluído. |
| Texto enviado como fallback e imagem base64 como entrada | Texto e Base64 aceitos. Se ambos são enviados, arquivo tem prioridade; erros de arquivo não são ocultados | Concluído. Etapas informam quais operações realmente rodaram. |
| OpenCV/Tesseract por executáveis externos configuráveis | Bibliotecas nativas empacotadas via JavaCPP/Bytedeco | Documentar diferença; o mecanismo externo ainda não foi implementado. |
| Chave OpenAI ativa interpretação no exemplo | Integração opcional exige `EXTRACTION_MODE=OPENAI` e chave | Documentar a opção explícita; verificar necessidade de reproduzir ativação automática. |
| Presença de campos e emissão até 90 dias | Contrato JSON usa as regras de presença e prazo, mais rejeição de data futura e OCR de baixa confiança. Multipart mantém regras adicionais de titular/UF/tipo documental | Concluído para o adaptador. Não exige nomeEsperado no JSON. |

O README de referência descreve integrações e também avisa que o repositório é uma preparação/documentação, sem implementação das funcionalidades naquele momento. Ele deve orientar o contrato e a discussão em aula, não ser interpretado como prova de que o nosso projeto já cumpre todos os detalhes.

## Incrementos registrados

1. Base existente publicada com documentação do estado inicial.
2. Payload JSON de exemplo e adaptador texto/Base64 implementados, com limites e validação de assinatura/MIME.
3. Testes de contrato para o payload do professor, exemplo gerado, datas limite, entradas inválidas, PDF e imagem com OCR real; testes antigos preservados.

O endpoint JSON não persiste resultados no histórico. A entrada textual é fornecida pelo cliente, não é prova de que um arquivo passou por OCR. Datas futuras e baixa confiança OCR são salvaguardas adicionais ao exemplo de regras do professor.

## Próximos incrementos reais

1. Validar PostgreSQL e OpenAI com os serviços configurados, quando disponíveis.
2. Confirmar com o professor se os executáveis externos e as versões exatas do exemplo são exigências ou apenas sugestões de implementação.

O convite de colaborador deve ser acompanhado nas configurações do repositório. A consulta de permissão realizada antes desta publicação retornou somente leitura para `KleberMeira`; esse resultado não permite distinguir um convite pendente de um convite ainda não enviado.
