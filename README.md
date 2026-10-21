# ⚽ NeaFutbol

<div align="center">

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java 17+](https://img.shields.io/badge/Java-17%20%7C%2021-ED8B00?logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Minecraft Paper](https://img.shields.io/badge/Platform-Paper%20%7C%20Purpur%201.21%2B-1e87f0)](https://papermc.io/)
[![GitHub](https://img.shields.io/badge/GitHub-NeaCol%2FNeaFutbol-black?logo=github)](https://github.com/NeaCol/NeaFutbol)

**Plugin de fútbol con físicas de proyectil, detección de goles por volumen de colisión y marcadores holográficos para Minecraft (Paper / Spigot 1.21+).**

</div>

---

**NeaFutbol** es un plugin de fútbol para servidores Minecraft (Spigot/Paper 1.21+). Proporciona mecánicas de juego en lobbies mediante simulación de físicas para el balón, detección automática de goles en porterías delimitadas, partición de equipos (Rojo vs Azul) y marcadores holográficos en tiempo real.

## Características

*   ⚽ **Físicas de Balón**: Potencia de disparo, pases y vector de elevación ajustables.
*   🥅 **Detección de Goles**: Registro automático mediante volúmenes de colisión delimitados.
*   👕 **Gestión de Equipos**: Distribución Rojo vs Azul con puntos de aparición independientes.
*   📊 **Marcadores Holográficos**: Marcador de goles y temporizador de partida en tiempo real.
*   ⚙️ **Configuración Centralizada**: Definición de duraciones, físicas y rangos en `config.yml`.
*   🌐 **Localización**: Cadenas de texto y mensajes configurables en `lang.yml`.

## Instalación

1.  Descargar el archivo `.jar` compilado.
2.  Copiar el archivo en el directorio `plugins/` del servidor.
3.  Reiniciar el servidor o cargar el plugin.
4.  Configurar las ubicaciones de juego (ver sección Configuración y Setup).

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
Delimitar el terreno de juego con espacio suficiente para la cancha y ambas porterías.

### 2. Definir Porterías
1.  Obtener la herramienta de selección mediante `/nf wand`.
2.  Seleccionar los dos extremos del volumen interior de la portería (click izquierdo para posición 1, click derecho para posición 2).
3.  Asignar la portería al equipo correspondiente con `/nf setgoal team1` o `/nf setgoal team2`.

### 3. Establecer Spawns
1.  Posicionarse en las coordenadas de reaparición del Equipo 1 y ejecutar `/nf setspawn team1`.
2.  Posicionarse en las coordenadas del Equipo 2 y ejecutar `/nf setspawn team2`.
3.  Posicionarse en el punto central de saque inicial y ejecutar `/nf setball`.

### 4. Marcador (Opcional)
Posicionarse en las coordenadas deseadas para el marcador flotante y ejecutar `/nf setholo`.

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
Archivo de definición para mensajes de chat, títulos y líneas del holograma. Compatible con códigos de color de Minecraft (`&a`) y hexadecimales (`&#RRGGBB`).

## Soporte y Reportes

Para reportar errores o solicitar mejoras, abrir una incidencia en [GitHub Issues](https://github.com/NeaCol/NeaFutbol/issues).

Desarrollado por **Nea#9654**.