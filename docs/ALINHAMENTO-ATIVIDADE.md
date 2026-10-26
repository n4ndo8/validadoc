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
| `GET /api/documentos/exemplo` retorna payload JSON | `GET /api/exemplo.pdf` retorna um PDF fictício | Adicionar o endpoint JSON de compatibilidade. |
| `POST /api/documentos/validar` aceita JSON | `POST /api/documentos` recebe multipart com arquivo e nome esperado | Adicionar adaptador para `nomeArquivo`, `mimeType`, `conteudoDocumento` e `imagemBase64`, preservando a tela. |
| Resposta com `etapas`, `dadosExtraidos` e `resultado` | Resposta com `dados`, `regras`, `status`, texto e avisos | Mapear o retorno do endpoint de compatibilidade para o contrato indicado. |
| Texto enviado como fallback e imagem base64 como entrada | Upload real e interpretação local por rótulos | Implementar a entrada textual sem apresentá-la como OCR realizado. |
| OpenCV/Tesseract por executáveis externos configuráveis | Bibliotecas nativas empacotadas via JavaCPP/Bytedeco | Documentar diferença; o mecanismo externo ainda não foi implementado. |
| Chave OpenAI ativa interpretação no exemplo | Integração opcional exige `EXTRACTION_MODE=OPENAI` e chave | Documentar a opção explícita; verificar necessidade de reproduzir ativação automática. |
| Presença de campos e emissão até 90 dias | Além dessas regras, confere titular informado, UF, indício do tipo documental, evidência e confiança OCR | Não exigir silenciosamente nomeEsperado no contrato JSON do professor, que não contém esse campo. Manter regras adicionais identificadas. |

O README de referência descreve integrações e também avisa que o repositório é uma preparação/documentação, sem implementação das funcionalidades naquele momento. Ele deve orientar o contrato e a discussão em aula, não ser interpretado como prova de que o nosso projeto já cumpre todos os detalhes.

## Próximos incrementos reais

1. Publicar a base existente com uma descrição clara do estado atual.
2. Adicionar o payload de exemplo JSON e teste de contrato.
3. Implementar a entrada JSON de texto/base64 com limites, validação de formato e indicação correta das etapas realmente executadas.
4. Testar o retorno esperado pelo professor sem prejudicar a interface existente.
5. Validar PostgreSQL e OpenAI com os serviços configurados, quando disponíveis.

O convite de colaborador deve ser acompanhado nas configurações do repositório. A consulta de permissão realizada antes desta publicação retornou somente leitura para `KleberMeira`; esse resultado não permite distinguir um convite pendente de um convite ainda não enviado.
