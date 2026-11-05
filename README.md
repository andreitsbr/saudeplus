# Saúde+

App Android simples e rápido para controlar se o medicamento foi tomado.

## Funcionalidades

- **Lista do dia**: todos os medicamentos ordenados por horário, com progresso ("2 de 4 tomados hoje").
- **Um toque para marcar**: botão **Tomei** registra o horário em que foi tomado; toque em **Tomado** para desfazer.
- **Atrasados em destaque**: horário fica vermelho quando passou e ainda não foi tomado.
- **Lembretes**: notificação no horário exato de cada medicamento, com o botão **✓ Tomei** direto na notificação. O ícone de sino no topo envia uma notificação de teste; se as notificações estiverem desativadas, aparece um aviso com o botão **Ativar**.
- **Reinício automático diário**: cada dia começa com tudo pendente; o registro dos últimos 90 dias fica salvo no aparelho.
- **Atualização dentro do app**: o botão ⟳ no topo verifica se há versão nova, baixa e instala. Ao abrir, o app também avisa quando há atualização.
- Funciona offline, sem conta e sem anúncios. Dados ficam só no celular.

## Como obter o APK

Cada push na branch `main` gera o APK no GitHub Actions e publica uma release
(`v1.<número do build>`) com o arquivo `saudeplus.apk`:
https://github.com/andreitsbr/saudeplus/releases/latest

O app consulta essa release para se atualizar. Todas as versões precisam ser assinadas
com a mesma chave, guardada nos secrets do repositório (`SIGNING_KEYSTORE_BASE64` e
`SIGNING_PASSWORD`). Sem esses secrets o build funciona, mas não publica release.

## Compilar localmente

Requer Android SDK (API 35) e JDK 17+:

```bash
./gradlew assembleDebug
# APK em app/build/outputs/apk/debug/app-debug.apk
```

Também pode abrir a pasta no Android Studio e clicar em *Run*.

## Tecnologia

Kotlin + Jetpack Compose (Material 3), `SharedPreferences` para armazenamento e `AlarmManager` para os lembretes. Android 8.0+ (minSdk 26).
