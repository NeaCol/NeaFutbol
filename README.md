# ⚽ NeaFutbol

<div align="center">

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java 17+](https://img.shields.io/badge/Java-17%20%7C%2021-ED8B00?logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Minecraft Paper](https://img.shields.io/badge/Platform-Paper%20%7C%20Purpur%201.21%2B-1e87f0)](https://papermc.io/)
[![GitHub](https://img.shields.io/badge/GitHub-NeaCol%2FNeaFutbol-black?logo=github)](https://github.com/NeaCol/NeaFutbol)

**Plugin de fútbol de alto rendimiento con físicas personalizadas, detección de goles y marcadores holográficos para Minecraft (Paper / Spigot 1.21+).**

</div>

---

**NeaFutbol** es un plugin de fútbol completo y optimizado para servidores de Minecraft (Spigot/Paper 1.21). Diseñado para lobbies y minijuegos, permite a los jugadores disfrutar de partidos de fútbol con físicas personalizadas, sistemas de equipos y marcadores holográficos.

## Características

*   ⚽ **Físicas de Balón Personalizadas**: Controla la potencia de disparo, pases y altura.
*   🥅 **Porterías y Detección de Goles**: Sistema automático de detección de goles.
*   👕 **Equipos**: Sistema de equipos (Rojo vs Azul) con puntos de aparición configurables.
*   📊 **Marcadores Holográficos**: Muestra el resultado y el tiempo restante en tiempo real.
*   🎨 **Totalmente Configurable**: Ajusta tiempos, mensajes, físicas y más desde `config.yml`.
*   🌐 **Idioma**: Mensajes 100% traducibles (`lang.yml`).

## Instalación

1.  Descarga el archivo `.jar`.
2.  Colócalo en la carpeta `plugins` de tu servidor.
3.  Reinicia el servidor.
4.  Configura las ubicaciones del juego (ver sección de Configuración).

## Comandos

El comando principal es `/neafutbol` (alias: `/nf`, `/futbol`).

### Jugadores
*   `/nf join`: Unirse a la partida.
*   `/nf leave`: Salir de la partida.
*   `/nf help`: Ver la lista de comandos.

### Administración (Requiere permiso)
*   `/nf wand`: Recibir la herramienta de selección (para definir áreas).
*   `/nf setspawn <team1|team2>`: Establecer el punto de aparición de un equipo.
*   `/nf setgoal <team1|team2>`: Establecer la portería de un equipo (requiere selección con wand).
*   `/nf setball`: Establecer el punto de aparición del balón.
*   `/nf setholo`: Crear/Mover el holograma del marcador.
*   `/nf removeholo`: Eliminar el holograma.
*   `/nf reload`: Recargar la configuración.

## Permisos

| Permiso | Descripción | Default |
| :--- | :--- | :--- |
| `neafutbol.use` | Permiso básico para usar comandos de jugador. | Todos |
| `neafutbol.join` | Permite unirse al juego. | Todos |
| `neafutbol.admin` | Acceso a comandos de configuración y administración. | OP |

## Configuración y Setup

### 1. Preparar la Cancha
Construye tu estadio de fútbol. Necesitarás espacio para las porterías y el campo de juego.

### 2. Definir Porterías
1.  Usa `/nf wand` para obtener la herramienta de selección.
2.  Selecciona las esquinas de la portería (bloques de aire dentro del arco) con click izquierdo y derecho.
3.  Ejecuta `/nf setgoal team1` (o `team2`).

### 3. Establecer Spawns
1.  Párate donde quieres que aparezca el Equipo 1 y usa `/nf setspawn team1`.
2.  Repite para el Equipo 2 con `/nf setspawn team2`.
3.  Párate en el centro del campo y usa `/nf setball` para el punto de inicio del balón.

### 4. Marcador (Opcional)
Párate donde quieras ver el marcador flotante y usa `/nf setholo`.

## Archivos de Configuración

### `config.yml`
```yaml
game:
  min-players: 2      # Mínimo de jugadores para iniciar
  max-players: 4      # Máximo de jugadores
  duration-seconds: 300 # Duración del partido
  countdown-seconds: 60 # Tiempo de espera en lobby

ball:
  skin: "MHF_CoconutB" # Skin de la cabeza del balón
  hitbox-range: 3.5    # Rango de golpeo
  kick-power: 1.2      # Fuerza del chute
```

### `lang.yml`
Personaliza todos los mensajes del chat, títulos y el texto del holograma. Admite códigos de color (ej. `&a`, `&#RRGGBB`).

## Soporte

Desarrollado por **Nea#9654**.