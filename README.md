# Twilight Forest Bossbar Packet Fix

Mod auxiliar para **Minecraft 1.21.1 com Fabric** que corrige o `Network Protocol
Error` / `NullPointerException` causado pela ordem dos pacotes de bossbar do
Twilight Forest. A falha pode desconectar o jogador durante uma luta e impedir
a reabertura do mundo enquanto o boss permanece próximo ao personagem.

[Baixar o JAR v1.0.0](https://github.com/paulosergioamorim/twilight-forest-bossbar-fix/releases/download/v1.0.0/tf-bossbar-packet-fix-1.0.0%2Bmc1.21.1.jar)
· [Releases](https://github.com/paulosergioamorim/twilight-forest-bossbar-fix/releases)

## Versões em que o erro foi observado

| Componente | Versão |
| --- | --- |
| Minecraft Java Edition | **1.21.1** |
| Twilight Forest Fabric | **4.8.734** |
| Twilight Forest Fabric, também testado com o mesmo erro | **4.8.629** |
| Fabric Loader utilizado | 0.19.3 |
| Fabric API utilizada | 0.116.14+1.21.1 |

O problema foi observado na luta contra os **Knight Phantoms**. Os logs
registraram a mesma falha com e sem Jade e Xaero. Trocar o Twilight entre
4.8.734 e 4.8.629 também não eliminou o erro.

**A abertura do mundo com o fix foi confirmada em Minecraft 1.21.1 e Twilight
Forest Fabric 4.8.734.** O JAR também declara compatibilidade com 4.8.629, mas
essa combinação não recebeu validação manual dentro do jogo. Outras versões
do Minecraft, outros loaders e outras versões do Twilight não foram validados.

## Instalação

1. Feche o Minecraft e faça um backup do mundo.
2. Baixe `tf-bossbar-packet-fix-1.0.0+mc1.21.1.jar` na página de Releases.
3. Coloque o arquivo na pasta `mods` da sua instância.
4. Abra o Minecraft e carregue o mundo normalmente, mantendo o Twilight Forest.

Requisitos declarados no JAR: Minecraft **1.21.1**, Java **21 ou superior**,
Fabric Loader **0.17.0 ou superior**, Fabric API **0.116.14+1.21.1 ou superior**
e Twilight Forest Fabric **4.8.629 ou 4.8.734**.

O fix atua no **cliente**. Em multiplayer, cada jogador afetado precisa
instalá-lo no próprio cliente; isso inclui o host de um mundo aberto em LAN.
Não é necessário instalá-lo em um servidor dedicado.

O mod mantém o JAR original do Twilight e os arquivos do mundo. Para
desinstalar, retire o JAR auxiliar de `mods` com o jogo fechado.

## Causa e correção

O erro observado no cliente era:

```text
java.lang.NullPointerException: Cannot invoke "net.minecraft.class_345.method_5408(float)"
because the return value of "java.util.Map.get(Object)" is null
    at net.minecraft.class_337$1.method_34100(class_337.java:123)
Incoming packet: clientbound/minecraft:boss_event
```

O Twilight cria a barra usando o pacote customizado `add_tf_boss_bar` e atualiza
seu progresso usando o pacote vanilla `boss_event`. O Fabric já despacha o
handler customizado para a thread do cliente, mas o `IPayloadContext` do Twilight
chama `Minecraft.execute` novamente. O executor reentrante adia essa segunda
tarefa; um UPDATE vanilla já na fila pode passar à frente do ADD e encontrar
uma barra que ainda não existe.

Este mod substitui apenas os handlers customizados de **ADD** e **STYLE**,
depois da inicialização dos mods. Continua executando os handlers originais
do Twilight, com um contexto que aplica a tarefa imediatamente quando já
está na thread do cliente. Chamadas de outras threads continuam sendo
agendadas. O protocolo e os outros handlers permanecem iguais.

Código do projeto original usado na análise, conferido com o bytecode instalado:

- [TFBossBarPacket.java](https://raw.githubusercontent.com/TeamTwilight/twilightforest-fabric/1.21.1/src/main/java/twilightforest/network/TFBossBarPacket.java)
- [IPayloadContext.java](https://raw.githubusercontent.com/TeamTwilight/twilightforest-fabric/1.21.1/src/main/java/twilightforest/network/IPayloadContext.java)
- [ServerTFBossBar.java](https://raw.githubusercontent.com/TeamTwilight/twilightforest-fabric/1.21.1/src/main/java/twilightforest/entity/boss/bar/ServerTFBossBar.java)

## Compilar e testar

A compilação é offline e utiliza os arquivos já instalados em uma instância
do **Modrinth App**. Abra essa instância pelo menos uma vez para gerar o cache
do Fabric. É necessário ter **Python 3** e um **JDK 21 ou superior** instalado.

```sh
python3 build.py --profile "/caminho/da/instancia" --test
```

Se `javac` não estiver no PATH, indique a pasta `bin` do JDK:

```sh
python3 build.py --profile "/caminho/da/instancia" --java-bin "/caminho/do/jdk/bin" --test
```

O resultado fica em `build/tf-bossbar-packet-fix-1.0.0+mc1.21.1.jar`. O código usa
os nomes intermediary do Minecraft 1.21.1 e gera classes compatíveis com Java 21.
Os JARs do Minecraft, Fabric e Twilight não são distribuídos neste repositório.

Os testes de regressão utilizam o executor real do Minecraft 1.21.1 e verificam:

- Reprodução do NPE com o agendamento original.
- Criação da barra antes da atualização vanilla de progresso.
- Aplicação das atualizações de estilo.
- Remoção sem recriar uma barra residual.
- Trabalho vindo de outra thread executado na thread do cliente.

A abertura do mundo foi confirmada manualmente após a instalação. Esses
testes não validam todas as lutas nem todas as combinações de mods.

## Integridade e licença

O arquivo `SHA256SUMS` contém o hash do JAR publicado na release v1.0.0.

Código deste mod auxiliar sob a licença [MIT](LICENSE). Este é um projeto
independente; o Twilight Forest e seus recursos pertencem aos respectivos
autores e seguem suas próprias licenças.
