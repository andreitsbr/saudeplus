# Saúde+

App Android simples e rápido para controlar se o medicamento foi tomado.

## Funcionalidades

- **Lista do dia**: todos os medicamentos ordenados por horário, com progresso ("2 de 4 tomados hoje").
- **Um toque para marcar**: botão **Tomei** registra o horário em que foi tomado; toque em **Tomado** para desfazer.
- **Atrasados em destaque**: horário fica vermelho quando passou e ainda não foi tomado.
- **Lembretes**: notificação no horário exato de cada medicamento, com o botão **✓ Tomei** direto na notificação. O ícone de sino no topo envia uma notificação de teste; se as notificações estiverem desativadas, aparece um aviso com o botão **Ativar**.
- **Reinício automático diário**: cada dia começa com tudo pendente; o registro dos últimos 90 dias fica salvo no aparelho.
- Funciona offline, sem conta e sem anúncios. Dados ficam só no celular.

## Como obter o APK

Cada push gera o APK automaticamente no GitHub Actions (workflow **Build APK**).
Abra a execução mais recente em *Actions* e baixe o artefato `saudeplus-apk`.

## Compilar localmente

Requer Android SDK (API 35) e JDK 17+:

```bash
./gradlew assembleDebug
# APK em app/build/outputs/apk/debug/app-debug.apk
```

Também pode abrir a pasta no Android Studio e clicar em *Run*.

## Tecnologia

Kotlin + Jetpack Compose (Material 3), `SharedPreferences` para armazenamento e `AlarmManager` para os lembretes. Android 8.0+ (minSdk 26).
